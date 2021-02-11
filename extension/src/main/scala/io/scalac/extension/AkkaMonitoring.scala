package io.scalac.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import akka.util.Timeout
import com.newrelic.telemetry.Attributes
import com.newrelic.telemetry.opentelemetry.`export`.NewRelicMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.`export`.IntervalMetricReader
import io.scalac.core.model.{ Module, SupportedVersion, Version }
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.ModuleInfo
import io.scalac.core.util.ModuleInfo.Modules
import io.scalac.extension.config.{ AkkaMonitoringConfig, CachingConfig }
import io.scalac.extension.http.CleanableRequestStorage
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.model._
import io.scalac.extension.persistence.{ CleanablePersistingStorage, CleanableRecoveryStorage }
import io.scalac.extension.service.CommonRegexPathService
import io.scalac.extension.upstream.{
  OpenTelemetryClusterMetricsMonitor,
  OpenTelemetryHttpMetricsMonitor,
  OpenTelemetryPersistenceMetricMonitor
}

import java.net.URI
import java.util.Collections
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Try

object AkkaMonitoring extends ExtensionId[AkkaMonitoring] {
  override def createExtension(system: ActorSystem[_]): AkkaMonitoring = {
    val config  = AkkaMonitoringConfig.apply(system.settings.config)
    val monitor = new AkkaMonitoring(system, config)

    val modules: Modules = ModuleInfo.extractModulesInformation(system.dynamicAccess.classLoader)

    modules
      .get(ModulesSupport.akkaActorModule)
      .fold {
        system.log.error("No akka version detected")
      }(akkaVersion => startMonitors(monitor, config, akkaVersion, modules, ModulesSupport))

    monitor
  }

  private def startMonitors(
    monitoring: AkkaMonitoring,
    config: AkkaMonitoringConfig,
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
      init: AkkaMonitoring => Unit
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
        cm.startSelfMemberMonitor()
        cm.startClusterEventsMonitor()
        cm.startClusterRegionsMonitor()
      }
    )
    initModule(
      akkaPersistenceTypedModule,
      modulesSupport.akkaPersistenceTyped,
      config.autoStart.akkaPersistence,
      cm => cm.startPersistenceMonitoring()
    )

    if (config.boot.metricsBackend) {
      config.backend.fold {
        system.log.error("Boot backend is set to true, but no configuration for backend found")
      } { backendConfig =>
        if (backendConfig.name.toLowerCase() == "newrelic") {
          system.log.info(s"Starting NewRelic backend with config ${backendConfig}")
          startNewRelicBackend(backendConfig.region, backendConfig.apiKey, backendConfig.serviceName)
        } else
          system.log.error(s"Backend ${backendConfig.name} not supported")
      }
    }
  }

  private def startNewRelicBackend(region: String, apiKey: String, serviceName: String): Unit = {
    val newRelicExporterBuilder = NewRelicMetricExporter
      .newBuilder()
      .apiKey(apiKey)
      .commonAttributes(new Attributes().put("service.name", serviceName))

    val newRelicExporter =
      if (region == "eu") {
        newRelicExporterBuilder
          .uriOverride(URI.create("https://metric-api.eu.newrelic.com/metric/v1"))
          .build()
      } else {
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

class AkkaMonitoring(private val system: ActorSystem[_], val config: AkkaMonitoringConfig) extends Extension {
  import system.log

  private val instrumentationName = "scalac_akka_metrics"
  private val actorSystemConfig   = system.settings.config
  private lazy val openTelemetryClusterMetricsMonitor = OpenTelemetryClusterMetricsMonitor(
    instrumentationName,
    actorSystemConfig
  )

  implicit private val timeout: Timeout = 5 seconds

  private def reflectiveIsInstanceOf(fqcn: String, ref: Any): Either[String, Unit] =
    Try(Class.forName(fqcn)).toEither.left.map {
      case _: ClassNotFoundException => s"Class ${fqcn} not found"
      case e                         => e.getMessage
    }.filterOrElse(_.isInstance(ref), s"Ref ${ref} is not instance of ${fqcn}").map(_ => ())

  private lazy val clusterNodeName: Option[Node] = {
    (for {
      _       <- reflectiveIsInstanceOf("akka.actor.typed.internal.adapter.ActorSystemAdapter", system)
      classic = system.classicSystem.asInstanceOf[ExtendedActorSystem]
      _       <- reflectiveIsInstanceOf("akka.cluster.ClusterActorRefProvider", classic.provider)
    } yield Cluster(classic).selfUniqueAddress.toNode).fold(message => {
      log.error(message)
      None
    }, Some.apply)
  }

  def startSelfMemberMonitor(): Unit = startClusterMonitor(ClusterSelfNodeEventsActor)

  def startClusterEventsMonitor(): Unit = startClusterMonitor(ClusterEventsMonitor)

  def startClusterRegionsMonitor(): Unit = startClusterMonitor(ClusterRegionsMonitorActor)

  private def startClusterMonitor[T <: ClusterMonitorActor: ClassTag](
    actor: T
  ): Unit = {
    val name = implicitly[ClassTag[T]].runtimeClass.getSimpleName
    clusterNodeName.fold {
      log.error(s"ActorSystem is not properly configured to start cluster monitor of type $name")
    } { _ =>
      log.debug(s"Starting cluster monitor of type $name")
      system.systemActorOf(
        Behaviors
          .supervise(actor(openTelemetryClusterMetricsMonitor))
          .onFailure[Exception](SupervisorStrategy.restart),
        name
      )
    }
  }

  def startPersistenceMonitoring(): Unit = {
    log.debug("Starting PersistenceEventsListener")

    val openTelemetryPersistenceMonitor = CachingMonitor(
      OpenTelemetryPersistenceMetricMonitor(instrumentationName, actorSystemConfig),
      CachingConfig.fromConfig(actorSystemConfig, "persistence")
    )

    system.systemActorOf(
      Behaviors
        .supervise(
          WithSelfCleaningState
            .clean(CleanableRecoveryStorage.withConfig(config.cleaning))
            .every(config.cleaning.every)(rs =>
              WithSelfCleaningState
                .clean(CleanablePersistingStorage.withConfig(config.cleaning))
                .every(config.cleaning.every) { ps =>
                  PersistenceEventsActor.apply(
                    openTelemetryPersistenceMonitor,
                    rs,
                    ps,
                    CommonRegexPathService,
                    clusterNodeName
                  )
                }
            )
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "persistenceAgentMonitor"
    )
  }

  def startHttpEventListener(): Unit = {
    log.info("Starting local http event listener")

    val openTelemetryHttpMonitor = CachingMonitor(
      OpenTelemetryHttpMetricsMonitor(instrumentationName, actorSystemConfig),
      CachingConfig.fromConfig(actorSystemConfig, "http")
    )
    val pathService = CommonRegexPathService

    system.systemActorOf(
      Behaviors
        .supervise(
          WithSelfCleaningState
            .clean(CleanableRequestStorage.withConfig(config.cleaning))
            .every(config.cleaning.every)(rs =>
              HttpEventsActor.apply(openTelemetryHttpMonitor, rs, pathService, clusterNodeName)
            )
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "httpEventMonitor"
    )
  }

}
