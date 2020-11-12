package io.scalac

import java.net.URI
import java.util.Collections
import java.{util => ju}

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityTypeKey}
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.opentelemetry.`export`.{NewRelicExporters, NewRelicMetricExporter}
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.`export`.IntervalMetricReader
import io.scalac.api.AccountRoutes
import io.scalac.domain.{AccountStateActor, JsonCodecs}
import io.scalac.infrastructure.PostgresAccountRepository
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api.Database

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

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

  val accountRepository = new PostgresAccountRepository(
    Database.forConfig("db", config)
  )

  val apiKey = config.getString("newrelic.api_key")

  val newRelicExporter = NewRelicMetricExporter
    .newBuilder()
    .apiKey(apiKey)
    .commonAttributes(new Attributes().put("service.name", "test_app"))
    .uriOverride(URI.create("https://metric-api.eu.newrelic.com/metric/v1"))
    .build()

  val intervalMetricReader = IntervalMetricReader
    .builder()
    .setMetricProducers(
      Collections.singleton(OpenTelemetrySdk.getMeterProvider.getMetricProducer)
    )
    .setExportIntervalMillis(5000)
    .setMetricExporter(newRelicExporter)
    .build()

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

  val accountRoutes = new AccountRoutes(accountsShards)

  val binding =
    accountRepository.createTableIfNotExists.flatMap { _ =>
      implicit val classicSystem = system.toClassic
      implicit val materializer  = ActorMaterializer()

      val host = config.getString("app.host")

      val port = config.getInt("app.port")
      logger.info(s"Starting http server at $host:$port")
      Http().bindAndHandle(accountRoutes.routes, host, port)
    }

  StdIn.readLine()

  binding
    .flatMap(_.unbind())
    .onComplete { _ =>
      system.terminate()
      NewRelicExporters.shutdown()
    }
}
