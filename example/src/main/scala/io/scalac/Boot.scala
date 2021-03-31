package io.scalac

import java.lang.instrument.Instrumentation

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, Entity, EntityTypeKey }
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{ get, path, _ }
import akka.http.scaladsl.server.Route
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout

import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.metrics
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers.{ marshaller => prommarsh }
import fr.davit.akka.http.metrics.prometheus.{ PrometheusRegistry, PrometheusSettings }
import io.opentelemetry.exporter.prometheus.PrometheusCollector
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.prometheus.client.{ Collector, CollectorRegistry }

import io.scalac.api.AccountRoutes
import io.scalac.domain.{ AccountStateActor, JsonCodecs }
import org.slf4j.LoggerFactory
import java.{ util => ju }

import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

import io.opentelemetry.api.metrics.GlobalMetricsProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider

import io.scalac.extension.config.InstrumentationLibrary

object Boot extends App with FailFastCirceSupport with JsonCodecs {
  val logger = LoggerFactory.getLogger(Boot.getClass)

  private val fallbackConfig = ConfigFactory
    .empty()
    .withValue(
      "app",
      ConfigValueFactory
        .fromMap(Map("host" -> "localhost", "port" -> 8080, "systemName" -> "Accounts").asJava)
    )
    .resolve

  def startUp(local: Boolean): Unit = {
    val baseConfig =
      if (local) ConfigFactory.load("local/application")
      else ConfigFactory.load()

    val config =
      baseConfig
        .withFallback(fallbackConfig)
        .resolve

    val collector: Collector = PrometheusCollector
      .builder()
      .setMetricProducer(InstrumentationLibrary.meterProducer)
      .build()

    val collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry

    collectorRegistry.register(collector)

    val settings = PrometheusSettings.default
    val registry = PrometheusRegistry(collectorRegistry, settings)

    val metricsRoutes: Route = (get & path("metrics"))(metrics(registry)(prommarsh))

    implicit val system =
      ActorSystem[Nothing](Behaviors.empty, config.getString("app.systemName"), config)

    implicit val executionContext = system.executionContext
    implicit val timeout: Timeout = 10 seconds

    AkkaManagement(system).start().onComplete {
      case Success(value)     => logger.info("Started akka management on uri: {}", value)
      case Failure(exception) => logger.error("Couldn't start akka management", exception)
    }

    if (!local) {
      ClusterBootstrap(system).start()
    }

    val entity = EntityTypeKey[AccountStateActor.Command]("accounts")

    val accountsShards = ClusterSharding(system)
      .init(Entity(entity) { entityContext =>
        AccountStateActor(
          ju.UUID.fromString(entityContext.entityId)
        )
      })

    val accountRoutes = new AccountRoutes(accountsShards)

    val host = config.getString("app.host")

    val port = config.getInt("app.port")
    logger.info(s"Starting http server at $host:$port")

    val binding = Http()
      .newServerAt(host, port)
      .bind(metricsRoutes ~ accountRoutes.routes)

    StdIn.readLine()

    sys.addShutdownHook {
      binding
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
    }
  }

  val local = sys.props.get("env").exists(_.toLowerCase() == "local")
  if (local) {
    logger.info("Starting application with static seed nodes")
  } else {
    logger.info("Staring application with ClusterBootstrap")
  }
  startUp(local)
}
