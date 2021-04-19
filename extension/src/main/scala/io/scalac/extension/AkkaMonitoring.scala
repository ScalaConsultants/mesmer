package io.scalac.extension
import akka.actor.ExtendedActorSystem
import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.Try

import io.scalac.core.actor.MutableActorMetricsStorage
import io.scalac.core.model.Module
import io.scalac.core.model.SupportedVersion
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.core.util.ModuleInfo
import io.scalac.core.util.ModuleInfo.Modules
import io.scalac.extension.config.AkkaMonitoringConfig
import io.scalac.extension.config.CachingConfig
import io.scalac.extension.config.InstrumentationLibrary
import io.scalac.extension.http.CleanableRequestStorage
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.persistence.CleanablePersistingStorage
import io.scalac.extension.persistence.CleanableRecoveryStorage
import io.scalac.extension.service.ActorTreeService
import io.scalac.extension.service.CachingPathService
import io.scalac.extension.service.actorTreeServiceKey
import io.scalac.extension.upstream.OpenTelemetryClusterMetricsMonitor
import io.scalac.extension.upstream.OpenTelemetryHttpMetricsMonitor
import io.scalac.extension.upstream.OpenTelemetryPersistenceMetricMonitor
import io.scalac.extension.upstream._

object AkkaMonitoring extends ExtensionId[AkkaMonitoring] {

  private val ExportInterval = 5.seconds

  override def createExtension(system: ActorSystem[_]): AkkaMonitoring = {
    val config  = AkkaMonitoringConfig(system.settings.config)
    val monitor = new AkkaMonitoring(system, config)

    val modules: Modules = ModuleInfo.extractModulesInformation(system.dynamicAccess.classLoader)

    modules
      .get(ModulesSupport.akkaActorModule)
      .fold(system.log.error("No akka version detected"))(_ => startMonitors(monitor, config, modules, ModulesSupport))

    monitor
  }

  private def startMonitors(
    monitoring: AkkaMonitoring,
    config: AkkaMonitoringConfig,
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
              system.log.info("Start monitoring module {}", module.name)
              init(monitoring)
            } else
              system.log.info("Supported version of {} detected, but auto-start is set to false", module.name)
        )

    initModule(
      akkaActorModule,
      modulesSupport.akkaActor,
      config.autoStart.akkaActor,
      am => {
        am.startActorMonitor()
        am.startStreamMonitor()
      }
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

  }

}

final class AkkaMonitoring(private val system: ActorSystem[_], val config: AkkaMonitoringConfig) extends Extension {
  import AkkaMonitoring.ExportInterval
  import system.log

  private val meter                              = InstrumentationLibrary.meter
  private val actorSystemConfig                  = system.settings.config
  private val openTelemetryClusterMetricsMonitor = OpenTelemetryClusterMetricsMonitor(meter, actorSystemConfig)
  import io.scalac.core.AkkaDispatcher._

  implicit private val timeout: Timeout = 5 seconds

  private def reflectiveIsInstanceOf(fqcn: String, ref: Any): Either[String, Unit] =
    Try(Class.forName(fqcn)).toEither.left.map {
      case _: ClassNotFoundException => s"Class $fqcn not found"
      case e                         => e.getMessage
    }.filterOrElse(_.isInstance(ref), s"Ref $ref is not instance of $fqcn").map(_ => ())

  private lazy val clusterNodeName: Option[Node] =
    (for {
      _ <- reflectiveIsInstanceOf("akka.actor.typed.internal.adapter.ActorSystemAdapter", system)
      classic = system.classicSystem.asInstanceOf[ExtendedActorSystem]
      _ <- reflectiveIsInstanceOf("akka.cluster.ClusterActorRefProvider", classic.provider)
    } yield Cluster(classic).selfUniqueAddress).fold(
      message => {
        log.error(message)
        None
      },
      nodeName => Some(nodeName.toNode)
    )

  def startActorTreeService(): Unit = {
    val actorSystemMonitor = OpenTelemetryActorSystemMonitor(
      meter,
      actorSystemConfig
    )

    val serviceRef = system.systemActorOf(
      Behaviors
        .supervise(
          ActorTreeService(
            actorSystemMonitor,
            clusterNodeName
          )
        )
        .onFailure(SupervisorStrategy.restart),
      "mesmerActorTreeService"
    )

    // publish service
    system.receptionist ! Register(actorTreeServiceKey, serviceRef.narrow[ActorTreeService.Command])
  }

  def startActorMonitor(): Unit = {
    log.debug("Starting actor monitor")

    val actorMonitor = OpenTelemetryActorMetricsMonitor(meter, actorSystemConfig)

    system.systemActorOf(
      Behaviors
        .supervise(
          ActorEventsMonitorActor(
            actorMonitor,
            clusterNodeName,
            ExportInterval,
            () => MutableActorMetricsStorage.empty
          )
        )
        .onFailure(SupervisorStrategy.restart),
      "mesmerActorMonitor",
      dispatcherSelector
    )
  }

  def startStreamMonitor(): Unit = {
    log.debug("Start stream monitor")

    val streamOperatorMonitor = OpenTelemetryStreamOperatorMetricsMonitor(meter, actorSystemConfig)

    val streamMonitor = CachingMonitor(
      OpenTelemetryStreamMetricsMonitor(meter, actorSystemConfig),
      CachingConfig.fromConfig(actorSystemConfig, ModulesSupport.akkaStreamModule)
    )

    system.systemActorOf(
      Behaviors
        .supervise(
          AkkaStreamMonitoring(streamOperatorMonitor, streamMonitor, clusterNodeName)
        )
        .onFailure(SupervisorStrategy.restart),
      "mesmerStreamMonitor",
      dispatcherSelector
    )
  }

  def startSelfMemberMonitor(): Unit = startClusterMonitor(ClusterSelfNodeEventsActor)

  def startClusterEventsMonitor(): Unit = startClusterMonitor(ClusterEventsMonitor)

  def startClusterRegionsMonitor(): Unit = startClusterMonitor(ClusterRegionsMonitorActor)

  private def startClusterMonitor[T <: ClusterMonitorActor: ClassTag](
    actor: T
  ): Unit = {
    val name = implicitly[ClassTag[T]].runtimeClass.getSimpleName
    clusterNodeName.fold {
      log.error("ActorSystem is not properly configured to start cluster monitor of type {}", name)
    } { _ =>
      log.debug("Starting cluster monitor of type {}", name)
      system.systemActorOf(
        Behaviors
          .supervise(actor(openTelemetryClusterMetricsMonitor))
          .onFailure[Exception](SupervisorStrategy.restart),
        name,
        dispatcherSelector
      )
    }
  }

  def startPersistenceMonitoring(): Unit = {
    log.debug("Starting PersistenceEventsListener")

    val cachingConfig = CachingConfig.fromConfig(actorSystemConfig, ModulesSupport.akkaPersistenceTypedModule)
    val openTelemetryPersistenceMonitor = CachingMonitor(
      OpenTelemetryPersistenceMetricMonitor(meter, actorSystemConfig),
      cachingConfig
    )
    val pathService = new CachingPathService(cachingConfig)

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
                    pathService,
                    clusterNodeName
                  )
                }
            )
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "persistenceAgentMonitor",
      dispatcherSelector
    )
  }

  def startHttpEventListener(): Unit = {
    log.info("Starting local http event listener")

    val cachingConfig = CachingConfig.fromConfig(actorSystemConfig, ModulesSupport.akkaHttpModule)

    val openTelemetryHttpMonitor =
      CachingMonitor(OpenTelemetryHttpMetricsMonitor(meter, actorSystemConfig), cachingConfig)

    val openTelemetryHttpConnectionMonitor =
      CachingMonitor(OpenTelemetryHttpConnectionMetricMonitor(meter, actorSystemConfig), cachingConfig)

    val pathService = new CachingPathService(cachingConfig)

    system.systemActorOf(
      Behaviors
        .supervise(
          WithSelfCleaningState
            .clean(CleanableRequestStorage.withConfig(config.cleaning))
            .every(config.cleaning.every)(rs =>
              HttpEventsActor
                .apply(openTelemetryHttpMonitor, openTelemetryHttpConnectionMonitor, rs, pathService, clusterNodeName)
            )
        )
        .onFailure[Exception](SupervisorStrategy.restart),
      "httpEventMonitor",
      dispatcherSelector
    )
  }

}
