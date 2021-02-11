package io.scalac

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{ get, path, _ }
import akka.http.scaladsl.server.Route
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.newrelic.telemetry.opentelemetry.`export`.NewRelicExporters
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.metrics
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers.{ marshaller => prommarsh }
import fr.davit.akka.http.metrics.prometheus.{ PrometheusRegistry, PrometheusSettings }
import io.opentelemetry.exporters.prometheus.PrometheusCollector
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.prometheus.client.{ Collector, CollectorRegistry }
import io.scalac.api.AccountRoutes
import io.scalac.domain.{ AccountStateActor, JsonCodecs }
import org.slf4j.LoggerFactory

import java.{ util => ju }
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

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

  val collector: Collector = PrometheusCollector
    .newBuilder()
    .setMetricProducer(OpenTelemetrySdk.getGlobalMeterProvider.getMetricProducer)
    .build()

  val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
  collectorRegistry.register(collector)

  val settings = PrometheusSettings.default
  val registry = PrometheusRegistry(collectorRegistry, settings)

  val metricsRoutes: Route = (get & path("metrics"))(metrics(registry)(prommarsh))

  implicit val system: ActorSystem[Nothing]       = ActorSystem[Nothing](Behaviors.empty, "Accounts", config)
  implicit val executionContext: ExecutionContext = system.executionContext
  implicit val timeout: Timeout                   = 10 seconds

  val entity = EntityTypeKey[AccountStateActor.Command]("accounts")

  val accountsShards = ClusterSharding(system)
    .init(Entity(entity) { entityContext =>
      AccountStateActor(
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

  val host = config.getString("app.host")

  val port = config.getInt("app.port")
  logger.info(s"Starting http server at $host:$port")

  val binding = Http()
    .newServerAt(host, port)
    .bind(accountRoutes.routes ~ metricsRoutes)

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
