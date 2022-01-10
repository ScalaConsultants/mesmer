package io.scalac.mesmer.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import akka.util.Timeout
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.Try
import io.scalac.mesmer.core.AkkaDispatcher
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.{ AkkaActorModule, AkkaHttpModule, AkkaPersistenceModule, _ }
import io.scalac.mesmer.core.module.Module._
import io.scalac.mesmer.extension.ActorEventsMonitorActor.ReflectiveActorMetricsReader
import io.scalac.mesmer.extension.AkkaMonitoring.ExportInterval
import io.scalac.mesmer.extension.actor.MutableActorMetricStorageFactory
import io.scalac.mesmer.extension.config.AkkaMonitoringConfig
import io.scalac.mesmer.extension.config.CachingConfig
import io.scalac.mesmer.extension.config.InstrumentationLibrary
import io.scalac.mesmer.extension.http.CleanableRequestStorage
import io.scalac.mesmer.extension.metric.CachingMonitor
import io.scalac.mesmer.extension.persistence.CleanablePersistingStorage
import io.scalac.mesmer.extension.persistence.CleanableRecoveryStorage
import io.scalac.mesmer.extension.service._
import io.scalac.mesmer.extension.upstream._
import io.opentelemetry.instrumentation.api.config.Config
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader

import scala.compat.java8.DurationConverters.FiniteDurationops

object AkkaMonitoring extends ExtensionId[AkkaMonitoring] {

  private val ExportInterval = 5.seconds

  def createExtension(system: ActorSystem[_]): AkkaMonitoring = {

    val otelConfig = Config
      .builder()
      .readProperties(new MesmerDefaultPropertyValues().getProperties)
      .readSystemProperties()
      .readEnvironmentVariables()
      .build()

    println("CREATE EXTENSION")

    val exampleString = otelConfig.getString("otel.mesmer.akkahttp.examplestring")
    println("AkkaMonitoring: " + exampleString)

    val someprop = otelConfig.getString("į")
    println("AkkaMonitoring, someprop: " + someprop)

    val config = AkkaMonitoringConfig.fromConfig(system.settings.config)

    new AkkaMonitoring(system, config)
  }
}

final class AkkaMonitoring(private val system: ActorSystem[_], val config: AkkaMonitoringConfig) extends Extension {

  import system.log

  private val meter             = InstrumentationLibrary.mesmerMeter
  private val actorSystemConfig = system.settings.config
  private val openTelemetryClusterMetricsMonitor =
    OpenTelemetryClusterMetricsMonitor(meter, AkkaClusterModule.fromConfig(actorSystemConfig), actorSystemConfig)

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

  private val dispatcher = AkkaDispatcher.safeDispatcherSelector(system)

  /*
    We combine global config published by agent with current config to account for actor system having
    different config file than agent. We take account only for 4 modules as those are only affected by agent.

    TODO (LEARNING): isn't it better to just rely only on the agent config? After all the extension without the agent is useless.
   */

  // TODO (LEARNING) where is the global config taken from? Why is agent involved in getting it?
  private lazy val akkaActorConfig: Option[AkkaActorModule.AkkaActorMetricsDef[Boolean]] =
    AkkaActorModule.globalConfiguration.map { config =>
      config.combine(AkkaActorModule.enabled(actorSystemConfig))
    }

  private lazy val akkaHttpConfig = Some(AkkaHttpModule.enabled(actorSystemConfig))
  //AkkaHttpModule.globalConfiguration.map { config =>
  //  config.combine(AkkaHttpModule.enabled(actorSystemConfig))
  //}

  private lazy val akkaPersistenceConfig =
    AkkaPersistenceModule.globalConfiguration.map { config =>
      config.combine(AkkaPersistenceModule.enabled(actorSystemConfig))
    }

  private lazy val akkaStreamConfig =
    AkkaStreamModule.globalConfiguration.map { config =>
      config.combine(AkkaStreamModule.enabled(actorSystemConfig))
    }

  private def startWithConfig[M <: Module](module: M, config: Option[M#All[Boolean]])(startUp: M#All[Boolean] => Unit)(
    implicit traverse: Traverse[M#All]
  ): Unit =
    config.fold {
      log.error("No global configuration found for {} - check if agent is installed.", module.name)
    } { moduleConfig =>
      if (!moduleConfig.exists(_ == true)) {
        log.warn(s"Module {} started but no metrics are enabled / supported", module.name)
      } else {
        log.debug("Starting up module {}", module.name)
        startUp(moduleConfig)
      }
    }

  /**
   * Start service that will monitor actor tree structure and publish refs on demand
   */
  def startActorTreeService(): Unit =
    startWithConfig[AkkaActorModule.type](AkkaActorModule, akkaActorConfig) { _ =>
      val actorSystemMonitor = OpenTelemetryActorSystemMonitor(
        meter,
        AkkaActorSystemModule.fromConfig(actorSystemConfig),
        actorSystemConfig
      )

      val actorConfigurationService = new ConfigBasedConfigurationService(system.settings.config)

      val serviceRef = system.systemActorOf(
        Behaviors
          .supervise(
            ActorTreeService(
              actorSystemMonitor,
              clusterNodeName,
              ReflectiveActorTreeTraverser,
              actorConfigurationService
            )
          )
          .onFailure(SupervisorStrategy.restart),
        "mesmerActorTreeService"
      )

      // publish service
      system.receptionist ! Register(actorTreeServiceKey, serviceRef.narrow[ActorTreeService.Command])
    }

  /**
   * Start actor that monitor actor metrics
   */
  def startActorMonitor(): Unit =
    startWithConfig[AkkaActorModule.type](AkkaActorModule, akkaActorConfig) { moduleConfig =>
      val actorMonitor =
        OpenTelemetryActorMetricsMonitor(meter, moduleConfig, actorSystemConfig)

      system.systemActorOf(
        Behaviors
          .supervise(
            ActorEventsMonitorActor(
              actorMonitor,
              clusterNodeName,
              ExportInterval,
              new MutableActorMetricStorageFactory[ActorKey],
              ReflectiveActorMetricsReader
            )
          )
          .onFailure(SupervisorStrategy.restart),
        "mesmerActorMonitor",
        dispatcher
      )
    }

  /**
   * Start actor monitoring stream performance
   */
  def startStreamMonitor(): Unit =
    startWithConfig[AkkaStreamModule.type](AkkaStreamModule, akkaStreamConfig) { moduleConfig =>
      log.debug("Start stream monitor")
      val streamOperatorMonitor = OpenTelemetryStreamOperatorMetricsMonitor(meter, moduleConfig, actorSystemConfig)

      val streamMonitor = CachingMonitor(
        OpenTelemetryStreamMetricsMonitor(meter, moduleConfig, actorSystemConfig),
        CachingConfig.fromConfig(actorSystemConfig, AkkaStreamModule)
      )

      system.systemActorOf(
        Behaviors
          .supervise(
            AkkaStreamMonitoring(streamOperatorMonitor, streamMonitor, clusterNodeName)
          )
          .onFailure(SupervisorStrategy.restart),
        "mesmerStreamMonitor",
        dispatcher
      )
    }

  def startSelfMemberMonitor(): Unit = startClusterMonitor(ClusterSelfNodeEventsActor)

  def startClusterEventsMonitor(): Unit = startClusterMonitor(ClusterEventsMonitor)

  def startClusterRegionsMonitor(): Unit = startClusterMonitor(ClusterRegionsMonitorActor)

  private def startClusterMonitor[T <: ClusterMonitorActor: ClassTag](
    actor: T
  ): Unit = {
    val name = classTag[T].runtimeClass.getSimpleName
    clusterNodeName.fold {
      log.error("ActorSystem is not properly configured to start cluster monitor of type {}", name)
    } { _ =>
      log.debug("Starting cluster monitor of type {}", name)
      system.systemActorOf(
        Behaviors
          .supervise(actor(openTelemetryClusterMetricsMonitor))
          .onFailure[Exception](SupervisorStrategy.restart),
        name,
        dispatcher
      )
    }
  }

  /**
   * Start actor responsible for measuring akka persistence metrics
   */
  def startPersistenceMonitor(): Unit =
    startWithConfig[AkkaPersistenceModule.type](AkkaPersistenceModule, akkaPersistenceConfig) { moduleConfig =>
      val cachingConfig = CachingConfig.fromConfig(actorSystemConfig, AkkaPersistenceModule)
      val openTelemetryPersistenceMonitor = CachingMonitor(
        OpenTelemetryPersistenceMetricsMonitor(meter, moduleConfig, actorSystemConfig),
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
        dispatcher
      )
    }

  /**
   * Start http actor responsible for calculating akka http metrics
   */
  def startHttpMonitor(): Unit =
    startWithConfig[AkkaHttpModule.type](AkkaHttpModule, akkaHttpConfig) { moduleConfig =>
      val cachingConfig = CachingConfig.fromConfig(actorSystemConfig, AkkaHttpModule)

      val openTelemetryHttpMonitor =
        CachingMonitor(OpenTelemetryHttpMetricsMonitor(meter, moduleConfig, actorSystemConfig), cachingConfig)

      val openTelemetryHttpConnectionMonitor =
        CachingMonitor(OpenTelemetryHttpConnectionMetricsMonitor(meter, moduleConfig, actorSystemConfig), cachingConfig)

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
        dispatcher
      )
    }

  private def autoStart(): Unit = {
    import config.{ autoStart => autoStartConfig }

    if (autoStartConfig.akkaActor || autoStartConfig.akkaStream) {
      log.debug("Start actor tree service")
      startActorTreeService()
    }
    if (autoStartConfig.akkaStream) {
      log.debug("Start akka stream service")

      startStreamMonitor()
    }
    if (autoStartConfig.akkaActor) {
      log.debug("Start akka actor service")
      startActorMonitor()
    }
    if (autoStartConfig.akkaHttp) {
      log.debug("Start akka persistence service")
      startHttpMonitor()
    }
    if (autoStartConfig.akkaPersistence) {
      log.debug("Start akka http service")

      startPersistenceMonitor()
    }

    if (autoStartConfig.akkaCluster) {
      log.debug("Start akka cluster service")
      startClusterEventsMonitor()
      startClusterRegionsMonitor()
      startSelfMemberMonitor()
    }

  }

  autoStart()

}
