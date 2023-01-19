package io.scalac.mesmer.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.Cluster
import com.typesafe.config.Config
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter
import org.slf4j.LoggerFactory

import scala.util.Try

import io.scalac.mesmer.core.AkkaDispatcher
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.Module._
import io.scalac.mesmer.core.module._
import io.scalac.mesmer.core.typeclasses.Traverse
import io.scalac.mesmer.extension.config.AkkaMonitoringConfig
import io.scalac.mesmer.extension.config.CachingConfig
import io.scalac.mesmer.extension.metric.CachingMonitor
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

  private val meter: Meter                 = GlobalOpenTelemetry.getMeter("mesmer-akka")
  private val actorSystemConfig: Config    = system.settings.config
  private val config: AkkaMonitoringConfig = AkkaMonitoringConfig.fromConfig(system.settings.config)
  private val dispatcher                   = AkkaDispatcher.safeDispatcherSelector(system)

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
