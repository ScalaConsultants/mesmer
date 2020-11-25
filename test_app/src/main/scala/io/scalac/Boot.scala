package io.scalac

import java.{util => ju}

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.AkkaManagement
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.newrelic.telemetry.opentelemetry.`export`.{NewRelicExporters, NewRelicMetricExporter}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.metrics
import fr.davit.akka.http.metrics.prometheus.{PrometheusRegistry, PrometheusSettings}
import io.opentelemetry.exporters.prometheus.PrometheusCollector
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.prometheus.client.{Collector, CollectorRegistry}
import io.scalac.api.AccountRoutes
import io.scalac.domain.{AccountStateActor, JsonCodecs}
import io.scalac.infrastructure.PostgresAccountRepository
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api.Database
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers.{marshaller => prommarsh}
import io.opentelemetry.sdk.metrics.`export`.IntervalMetricReader

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import scala.util.{Failure, Success}

object Boot extends App with FailFastCirceSupport with JsonCodecs {

  val logger = LoggerFactory.getLogger(Boot.getClass)

  val config = ConfigFactory
    .load()
    .withFallback(
      ConfigFactory
        .empty()
        .withValue(
          "app",
          ConfigValueFactory
            .fromMap(Map("host" -> "localhost", "port" -> 8080).asJava)
        )
    )
    .resolve

  val accountRepository = new PostgresAccountRepository(
    Database.forConfig("db", config)
  )
  
  val collector: Collector = PrometheusCollector
    .newBuilder()
    .setMetricProducer(OpenTelemetrySdk.getMeterProvider.getMetricProducer)
    .build()

  val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
  collectorRegistry.register(collector)

  val settings = PrometheusSettings.default
  val registry = PrometheusRegistry(collectorRegistry, settings)


  val metricsRoutes: Route = (get & path("metrics"))(metrics(registry))

  implicit val system =
    ActorSystem[Nothing](Behaviors.empty, "Accounts", config)
  implicit val executionContext = system.executionContext
  implicit val timeout: Timeout = 10 seconds

  val entity = EntityTypeKey[AccountStateActor.Command]("accounts")

  val accountsShards = ClusterSharding(system)
    .init(Entity(entity) { entityContext =>
      AccountStateActor(
        accountRepository,
        ju.UUID.fromString(entityContext.entityId)
      )
    })

  AkkaManagement(system)
    .start()
    .onComplete {
      case Success(value)     => logger.info(s"Started akka management on uri: ${value}")
      case Failure(exception) => logger.error("Coundn't start akka management", exception)
    }

  val accountRoutes = new AccountRoutes(accountsShards)

  val binding =
    accountRepository.createTableIfNotExists.flatMap { _ =>
      implicit val classicSystem = system.toClassic
      implicit val materializer  = ActorMaterializer()

      val host = config.getString("app.host")

      val port = config.getInt("app.port")
      logger.info(s"Starting http server at $host:$port")
      Http().bindAndHandle(accountRoutes.routes ~ metricsRoutes, host, port)
    }

  StdIn.readLine()

  sys.addShutdownHook {
    binding
      .flatMap(_.unbind())
      .onComplete { _ =>
        system.terminate()
        NewRelicExporters.shutdown()
      }
  }
}
