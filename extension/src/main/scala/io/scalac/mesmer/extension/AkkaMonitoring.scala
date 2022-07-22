package io.scalac.mesmer.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import akka.util.Timeout
import com.typesafe.config.Config
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.Try

import io.scalac.mesmer.core.AkkaDispatcher
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.Module._
import io.scalac.mesmer.core.module._
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.extension.config.AkkaMonitoringConfig
import io.scalac.mesmer.extension.config.CachingConfig
import io.scalac.mesmer.extension.metric.CachingMonitor
import io.scalac.mesmer.extension.persistence.CleanablePersistingStorage
import io.scalac.mesmer.extension.persistence.CleanableRecoveryStorage
import io.scalac.mesmer.extension.service._
import io.scalac.mesmer.extension.upstream._

object AkkaMonitoring extends ExtensionId[AkkaMonitoring] {
  def createExtension(system: ActorSystem[_]): AkkaMonitoring = new AkkaMonitoring(system)
}

final class AkkaMonitoring(system: ActorSystem[_]) extends Extension {

  private val log = LoggerFactory.getLogger(classOf[AkkaMonitoring])

  private val clusterNodeName: Option[Node] =
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

  private val askTimeout: Timeout          = 5 seconds
  private val meter: Meter                 = GlobalOpenTelemetry.getMeter("mesmer-akka")
  private val actorSystemConfig: Config    = system.settings.config
  private val config: AkkaMonitoringConfig = AkkaMonitoringConfig.fromConfig(system.settings.config)
  /*
   We combine global config published by agent with current config to account for actor system having
   different config file than agent. We take account only for 4 modules as those are only affected by agent.
   */
  private val akkaActorConfig = AkkaActorModule.enabled

  private val openTelemetryClusterMetricsMonitor: OpenTelemetryClusterMetricsMonitor =
    OpenTelemetryClusterMetricsMonitor(meter, AkkaClusterModule.enabled, actorSystemConfig)
  private val dispatcher = AkkaDispatcher.safeDispatcherSelector(system)

  private def reflectiveIsInstanceOf(fqcn: String, ref: Any): Either[String, Unit] =
    Try(Class.forName(fqcn)).toEither.left.map {
      case _: ClassNotFoundException => s"Class $fqcn not found"
      case e                         => e.getMessage
    }.filterOrElse(_.isInstance(ref), s"Ref $ref is not instance of $fqcn").map(_ => ())

  private def autoStart(): Unit = {
    import config.{ autoStart => autoStartConfig }

    if (autoStartConfig.akkaStream) {
      log.debug("Start akka stream service")

      startStreamMonitor()
    }

    if (autoStartConfig.akkaPersistence) {
      log.debug("Start akka persistence service")

      startPersistenceMonitor()
    }

    if (autoStartConfig.akkaCluster) {
      log.debug("Start akka cluster service")
      startClusterEventsMonitor()
      startClusterRegionsMonitor()
      startSelfMemberMonitor()
    }
  }

  private def startStreamMonitor(): Unit = {
    val akkaStreamConfig =
      AkkaStreamModule.enabled

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
  }

  private def startSelfMemberMonitor(): Unit = startClusterMonitor(ClusterSelfNodeEventsActor)

  private def startClusterEventsMonitor(): Unit = startClusterMonitor(ClusterEventsMonitor)

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

  private def startClusterRegionsMonitor(): Unit = startClusterMonitor(ClusterRegionsMonitorActor)

  private def startPersistenceMonitor(): Unit = {
    val akkaPersistenceConfig = AkkaPersistenceModule.enabled

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
  }

  private def startWithConfig[M <: Module](module: M, config: M#All[Boolean])(startUp: M#All[Boolean] => Unit)(implicit
    traverse: Traverse[M#All]
  ): Unit =
    if (!config.exists(_ == true)) {
      log.warn(s"Module {} started but no metrics are enabled / supported", module.name)
    } else {
      log.debug("Starting up module {}", module.name)
      startUp(config)
    }

  autoStart()
}
