package io.scalac.extension

import java.net.URI
import java.util.Collections

import akka.actor.CoordinatedShutdown
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Extension, ExtensionId, SupervisorStrategy }
import akka.cluster.typed.{ ClusterSingleton, SingletonActor }
import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.opentelemetry.`export`.NewRelicMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.`export`.IntervalMetricReader
import io.scalac.core.model.{ Module, SupportedVersion, Version }
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.ModuleInfo
import io.scalac.core.util.ModuleInfo.Modules
import io.scalac.extension.config.ClusterMonitoringConfig
import io.scalac.extension.service.CommonRegexPathService
import io.scalac.extension.upstream.{
  NewRelicEventStream,
  OpenTelemetryClusterMetricsMonitor,
  OpenTelemetryHttpMetricsMonitor,
  OpenTelemetryPersistenceMetricMonitor
}
import org.slf4j.Logger

import scala.concurrent.duration._

object ClusterMonitoring extends ExtensionId[ClusterMonitoring] {
  override def createExtension(system: ActorSystem[_]): ClusterMonitoring = {
    val config  = ClusterMonitoringConfig.apply(system.settings.config)
    val monitor = new ClusterMonitoring(system, config)

    val modules: Modules = ModuleInfo.extractModulesInformation(system.dynamicAccess.classLoader)

    modules
      .get(ModulesSupport.akkaActorModule)
      .fold {
        system.log.error("No akka version detected")
      }(akkaVersion => startMonitors(monitor, config, akkaVersion, modules, ModulesSupport))

    monitor
  }

  private def startMonitors(
    monitoring: ClusterMonitoring,
    config: ClusterMonitoringConfig,
    akkaVersion: Version,
    modules: Modules,
    modulesSupport: ModulesSupport
  ): Unit = {
    import ModulesSupport._
    import monitoring.system

    def initModule(
      module: Module,
      supportedVersion: SupportedVersion,
      autoStart: Boolean,
      init: ClusterMonitoring => Unit
    ): Unit =
      modules
        .get(module)
        .toRight(s"No version of ${module.name} detected")
        .filterOrElse(supportedVersion.supports, s"Unsupported version of ${module.name} detected")
        .fold(
          err => system.log.error(err),
          _ =>
            if (autoStart) {
              system.log.info(s"Start monitoring module ${module.name}")
              init(monitoring)
            } else
              system.log.info(s"Supported version of ${module.name} detected, but auto-start is set to false")
        )

    initModule(akkaHttpModule, modulesSupport.akkaHttp, config.autoStart.akkaHttp, _.startHttpEventListener())
    initModule(
      akkaClusterTypedModule,
      modulesSupport.akkaClusterTyped,
      config.autoStart.akkaCluster,
      cm => {
        cm.startMemberMonitor()
        cm.startReachabilityMonitor()
      }
    )
    initModule(
      akkaPersistenceTypedModule,
      modulesSupport.akkaPersistenceTyped,
      config.autoStart.akkaPersistence,
      cm => cm.startAgentListener()
    )

    if (config.boot.metricsBackend) {
      config.backend.fold {
        system.log.error("Boot backend is set to true, but no configuration for backend found")
      } { backendConfig =>
        if (backendConfig.name.toLowerCase() == "newrelic") {
          system.log.error(s"Starting NewRelic backend with config ${backendConfig}")
          startNewRelicBackend(backendConfig.region, backendConfig.apiKey, backendConfig.serviceName)(system.log)
        } else
          system.log.error(s"Backend ${backendConfig.name} not supported")
      }
    }
  }

  private def startNewRelicBackend(region: String, apiKey: String, serviceName: String)(logger: Logger): Unit = {
    val newRelicExporterBuilder = NewRelicMetricExporter
      .newBuilder()
      .apiKey(apiKey)
      .commonAttributes(new Attributes().put("service.name", serviceName))

    val newRelicExporter =
      if (region == "eu") {
        logger.error("Overriding NR uri")
        newRelicExporterBuilder
          .uriOverride(URI.create("https://metric-api.eu.newrelic.com/metric/v1"))
          .build()
      } else {
        logger.error(s"Going with default uri -> region: ${region}")
        newRelicExporterBuilder.build()
      }

    IntervalMetricReader
      .builder()
      .setMetricProducers(
        Collections.singleton(OpenTelemetrySdk.getGlobalMeterProvider.getMetricProducer)
      )
      .setExportIntervalMillis(5000)
      .setMetricExporter(newRelicExporter)
      .build()
  }

}

class ClusterMonitoring(private val system: ActorSystem[_], val config: ClusterMonitoringConfig) extends Extension {

  private val instrumentationName = "scalac_akka_metrics"
  private val actorSystemConfig   = system.settings.config
  import system.log
  private lazy val openTelemetryClusterMetricsMonitor =
    OpenTelemetryClusterMetricsMonitor(
      instrumentationName,
      actorSystemConfig
    )

  def startMemberMonitor(): Unit = {
    log.info("Starting member monitor")

    system.systemActorOf(
      Behaviors
        .supervise(
          ClusterSelfNodeMetricGatherer
            .apply(
              openTelemetryClusterMetricsMonitor,
              initTimeout = Some(60.seconds)
            )
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "localSystemMemberMonitor"
    )
  }

  def startReachabilityMonitor(): Unit = {

    log.info("Starting reachability monitor")

    NewRelicEventStream.NewRelicConfig
      .fromConfig(system.settings.config)
      .fold(
        errorMessage =>
          system.log
            .error(s"Couldn't start reachability monitor -> ${errorMessage}"),
        config => {
          implicit val classicSystem = system.classicSystem
          val newRelicEventStream    = new NewRelicEventStream(config)

          ClusterSingleton(system)
            .init(
              SingletonActor(
                Behaviors
                  .supervise(ClusterEventsMonitor(openTelemetryClusterMetricsMonitor))
                  .onFailure[Exception](SupervisorStrategy.restart),
                "MemberMonitoringActor"
              )
            )

          CoordinatedShutdown(classicSystem).addTask(
            CoordinatedShutdown.PhaseBeforeActorSystemTerminate,
            "closeNewRelicEventStream"
          )(() => newRelicEventStream.shutdown())
        }
      )
  }

  def startAgentListener(): Unit = {
    log.info("Starting local agent listener")

    val openTelemetryPersistenceMonitor = OpenTelemetryPersistenceMetricMonitor(instrumentationName, actorSystemConfig)

    system.systemActorOf(
      Behaviors
        .supervise(
          PersistenceEventsListener.apply(CommonRegexPathService, openTelemetryPersistenceMonitor)
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "persistenceAgentMonitor"
    )
  }

  def startHttpEventListener(): Unit = {
    log.info("Starting local http event listener")

    val openTelemetryHttpMonitor = OpenTelemetryHttpMetricsMonitor(instrumentationName, actorSystemConfig)
    val pathService              = CommonRegexPathService

    system.systemActorOf(
      Behaviors
        .supervise(HttpEventsActor.apply(openTelemetryHttpMonitor, pathService))
        .onFailure[Exception](SupervisorStrategy.restart),
      "httpEventMonitor"
    )
  }
}
