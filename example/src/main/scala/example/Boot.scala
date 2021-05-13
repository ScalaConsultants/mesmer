package example

import java.util.Collections

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.http.scaladsl.Http
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import example.api.AccountRoutes
import example.domain.AccountStateActor
import example.domain.JsonCodecs
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.StdIn
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.extension.config.InstrumentationLibrary

object Boot extends App with FailFastCirceSupport with JsonCodecs {
  val logger: Logger = LoggerFactory.getLogger(Boot.getClass)

  private val fallbackConfig = ConfigFactory
    .empty()
    .withValue(
      "app",
      ConfigValueFactory
        .fromMap(Map("host" -> "localhost", "port" -> 8080, "systemName" -> "Accounts").asJava)
    )
    .resolve

  def initOpenTelemetryMetrics(): IntervalMetricReader = {
    val metricExporter: OtlpGrpcMetricExporter = OtlpGrpcMetricExporter.getDefault()

    val meterProvider: SdkMeterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal()
    val intervalMetricReader: IntervalMetricReader =
        IntervalMetricReader.builder()
            .setMetricExporter(metricExporter)
            .setMetricProducers(Collections.singleton(meterProvider))
            .setExportIntervalMillis(1000)
            .buildAndStart()

    return intervalMetricReader
  }

  def startUp(local: Boolean): Unit = {
    val baseConfig =
      if (local) ConfigFactory.load("local/application")
      else ConfigFactory.load()

    val config =
      baseConfig
        .withFallback(fallbackConfig)
        .resolve

    val metricReader = initOpenTelemetryMetrics()

    implicit val system: ActorSystem[Nothing] =
      ActorSystem[Nothing](Behaviors.empty, config.getString("app.systemName"), config)

    implicit val executionContext: ExecutionContext = system.executionContext
    implicit val timeout: Timeout                   = 10 seconds

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
          java.util.UUID.fromString(entityContext.entityId)
        )
      })

    val accountRoutes = new AccountRoutes(accountsShards)

    val host = config.getString("app.host")

    val port = config.getInt("app.port")
    logger.info(s"Starting http server at $host:$port")

    val binding = Http()
      .newServerAt(host, port)
      .bind(accountRoutes.routes)

    StdIn.readLine()
    
    sys.addShutdownHook(metricReader.shutdown())

    sys.addShutdownHook {
      binding
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
    }

  }

  val local: Boolean = sys.props.get("env").exists(_.toLowerCase() == "local")
  if (local) {
    logger.info("Starting application with static seed nodes")
  } else {
    logger.info("Staring application with ClusterBootstrap")
  }
  startUp(local)
}
