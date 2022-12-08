package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.typed.Cluster
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.core.cluster.ClusterNode.ActorSystemOps
import io.scalac.mesmer.core.event.Service.streamService
import io.scalac.mesmer.core.event.StreamEvent
import io.scalac.mesmer.core.event.StreamEvent.LastStreamStats
import io.scalac.mesmer.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.mesmer.core.model.ShellInfo
import io.scalac.mesmer.core.util.Retry
import io.scalac.mesmer.core.util.stream
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitor.StreamStatsReceived

class AkkaStreamMonitor(actorSystem: ActorSystem[_]) extends Extension {
  private val node = actorSystem.clusterNodeName

  private val metrics = new AkkaStreamMetrics(node)

  private val interval = AkkaStreamConfig.metricSnapshotRefreshInterval(actorSystem.classicSystem)

  def start(): Behavior[StreamStatsReceived] = Behaviors.setup[StreamStatsReceived] { ctx =>
    actorSystem.receptionist ! Register(
      streamService.serviceKey,
      ctx.messageAdapter[StreamEvent](StreamStatsReceived.apply)
    )
    actorSystem.scheduler.scheduleWithFixedDelay(interval, interval)(() => captureSnapshot())(
      actorSystem.executionContext
    )

    Behaviors.receiveMessage[StreamStatsReceived] {
      case StreamStatsReceived(StreamInterpreterStats(ref, _, shellInfo)) =>
        collected.put(ref, shellInfo)
        Behaviors.same
      case StreamStatsReceived(LastStreamStats(ref, _, shellInfo)) =>
        collected.put(ref, Set(shellInfo))
        Behaviors.same
    }
  }

  private val collected: mutable.Map[ActorRef, Set[ShellInfo]] = mutable.Map.empty

  private def captureSnapshot(): Unit = {
    val current = collected.toMap
    collected.clear()

    val streamShells = current.groupBy { case (ref, _) =>
      stream.subStreamNameFromActorRef(ref)
    }
    val streamNames = streamShells.keySet.map(_.streamName)
    metrics.setRunningStreamsTotal(streamNames.size)
    metrics.setRunningActorsTotal(current.size)
  }

  actorSystem.systemActorOf(start(), "mesmerStreamMonitor")

}

object AkkaStreamMonitor {
  private val log = LoggerFactory.getLogger(classOf[AkkaStreamMonitor])

  final case class StreamStatsReceived(actorInterpreterStats: StreamEvent)

  def registerExtension(system: akka.actor.ActorSystem): Unit =
    new Thread(new Runnable() {
      override def run(): Unit =
        Retry.retryWithPrecondition(10, 2.seconds)(system.toTyped.hasExtension(Cluster))(register(system)) match {
          case Failure(_) =>
            Retry.retry(10, 2.seconds)(register(system)) match {
              case Failure(error) =>
                log.error(s"Failed to install the Akka Stream Monitoring Extension. Reason: $error")
              case Success(_) =>
                log.info("Successfully installed the Akka Stream Monitoring Extension.")
            }
          case Success(_) =>
            log.info("Successfully installed the Akka Stream Monitoring Extension.")
        }
    })
      .start()

  private def register(system: akka.actor.ActorSystem) = system.toTyped.registerExtension(AkkaStreamMonitorExtensionId)
}

object AkkaStreamMonitorExtensionId extends ExtensionId[AkkaStreamMonitor] {
  override def createExtension(system: ActorSystem[_]): AkkaStreamMonitor = new AkkaStreamMonitor(system)
}
