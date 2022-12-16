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

import io.scalac.mesmer.core.akka.stream.ConnectionsIndexCache
import io.scalac.mesmer.core.cluster.ClusterNode.ActorSystemOps
import io.scalac.mesmer.core.event.Service.streamService
import io.scalac.mesmer.core.event.StreamEvent
import io.scalac.mesmer.core.event.StreamEvent.LastStreamStats
import io.scalac.mesmer.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.mesmer.core.model.ShellInfo
import io.scalac.mesmer.core.model.stream.ConnectionStats
import io.scalac.mesmer.core.model.stream.StageInfo
import io.scalac.mesmer.core.util.Retry
import io.scalac.mesmer.core.util.stream
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension.StreamStatsReceived

final class AkkaStreamMonitorExtension(actorSystem: ActorSystem[_]) extends Extension {
  private val node = actorSystem.clusterNodeName

  private val metrics = new AkkaStreamMetrics(node)

  private val interval = AkkaStreamConfig.metricSnapshotRefreshInterval(actorSystem.classicSystem)
  // FIXME: move to config
  private val indexCache = ConnectionsIndexCache.bounded(100)

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

    /*
     FIXME:
     - process "current" shell infos based on how it's done in `AkkaStreamMonitoring`
     - ConnectionsIndexCache is now in `core`
     - based on the processed shells, create `metrics.runningOperators` metric
     - compare reported metrics to the version 0.7.0
     - if it doesn't match, try to append shellInfos instead of replacing them in lines 50 and 53
     */

  }

  private def computeSnapshotEntries(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    distinct: Boolean,
    extractFunction: ConnectionStats => Int
  ): Seq[StageInfo] =
    // FIXME: Simplify
    if (connectionStats.nonEmpty) {
      if (distinct) {
        // optimization for simpler graphs
        connectionStats.toSeq.map(_ => stage)
      } else {
        connectionStats
          .map(extractFunction)
          .toSeq
          .map(_ => stage)
      }
    } else {
      Seq(stage)
    }

  actorSystem.systemActorOf(start(), "mesmerStreamMonitor")

}

object AkkaStreamMonitorExtension {
  private val log           = LoggerFactory.getLogger(classOf[AkkaStreamMonitorExtension])
  private val retryLimit    = 10
  private val retryInterval = 2.seconds

  final case class StreamStatsReceived(actorInterpreterStats: StreamEvent)

  def registerExtension(system: akka.actor.ActorSystem): Unit =
    new Thread(new Runnable() {
      override def run(): Unit =
        registerWithClusterOrElse(system)(registerWithNoCluster(system))
    })
      .start()

  private def registerWithClusterOrElse(system: akka.actor.ActorSystem)(failoverFn: => Unit): Unit =
    Retry.retryWithPrecondition(retryLimit, retryInterval)(system.toTyped.hasExtension(Cluster))(
      register(system)
    ) match {
      case Failure(_) =>
        failoverFn
      case Success(_) =>
        log.info("Successfully installed the Akka Stream Monitoring Extension.")
    }

  private def registerWithNoCluster(system: akka.actor.ActorSystem): Unit =
    Retry.retry(retryLimit, retryInterval)(register(system)) match {
      case Failure(error) =>
        log.error(s"Failed to install the Akka Stream Monitoring Extension. Reason: $error")
      case Success(_) =>
        log.info("Successfully installed the Akka Stream Monitoring Extension.")
    }

  private def register(system: akka.actor.ActorSystem) = system.toTyped.registerExtension(AkkaStreamMonitorExtensionId)
}

object AkkaStreamMonitorExtensionId extends ExtensionId[AkkaStreamMonitorExtension] {
  override def createExtension(system: ActorSystem[_]): AkkaStreamMonitorExtension = new AkkaStreamMonitorExtension(
    system
  )
}
