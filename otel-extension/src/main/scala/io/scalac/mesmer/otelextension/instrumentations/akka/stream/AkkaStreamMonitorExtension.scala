package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import io.opentelemetry.api.common.Attributes
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.core.event.Service.streamService
import io.scalac.mesmer.core.event.StreamEvent
import io.scalac.mesmer.core.event.StreamEvent.LastStreamStats
import io.scalac.mesmer.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.mesmer.core.model.StreamInfo
import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.core.model.Tag.StreamName
import io.scalac.mesmer.core.model.stream.StreamStats
import io.scalac.mesmer.core.model.stream.StreamStatsBuilder
import io.scalac.mesmer.core.util.ClassicActorSystemOps.ActorSystemOps
import io.scalac.mesmer.core.util.Retry
import io.scalac.mesmer.core.util.TypedActorSystemOps.{ ActorSystemOps => TypedActorSystemOps }
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMetrics.streamNameAttribute
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension.StreamStatsReceived

final class AkkaStreamMonitorExtension(actorSystem: ActorSystem[_]) extends Extension {

  private lazy val metrics = new AkkaStreamMetrics(actorSystem)

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
      case StreamStatsReceived(StreamInterpreterStats(ref, streamName, shellInfo)) =>
        collected.put(ref, StreamInfo(streamName, shellInfo))
        Behaviors.same
      case StreamStatsReceived(LastStreamStats(ref, streamName, shellInfo)) =>
        collected.put(ref, StreamInfo(streamName, Set(shellInfo)))
        Behaviors.same
    }
  }

  private val collected: mutable.Map[ActorRef, StreamInfo] = mutable.Map.empty

  private def captureSnapshot(): Unit = {
    val current = collected.toMap
    collected.clear()

    val streamShells: Map[Tag.SubStreamName, Map[ActorRef, StreamInfo]] =
      current.groupBy { case (ref, streamInfo) => streamInfo.subStreamName }
    val streamNames = streamShells.keySet.map(_.streamName)

    collectStreamStats(current)
  }

  private def collectStreamStats(current: Map[ActorRef, StreamInfo]) = {
    val nodeAttribute: Attributes = AkkaStreamAttributes.forNode(actorSystem.clusterNodeName)
    val currentStreamStats        = mutable.Map.empty[StreamName, StreamStatsBuilder]

    val streamStats = current.values.map { streamInfo =>
      val subStreamName = streamInfo.subStreamName
      val stats =
        currentStreamStats.getOrElseUpdate(subStreamName.streamName, new StreamStatsBuilder(subStreamName.streamName))
      stats.incActors()
    }.map(_.build).toSeq

    metrics.setRunningActorsTotal(streamStats.foldLeft(0L)(_ + _.actors), nodeAttribute)
    // FIXME: or just: metrics.setRunningActorsTotal(current.size)???
    metrics.setRunningStreamsTotal(currentStreamStats.size, nodeAttribute)
    setStreamProcessedMessages(nodeAttribute, streamStats)
  }

  private def setStreamProcessedMessages(nodeAttribute: Attributes, streamStats: Seq[StreamStats]): Unit = {
    val builder = nodeAttribute.toBuilder

    val processesMessages = streamStats.map { stats =>
      val attributes = builder.put(streamNameAttribute, stats.streamName.name).build()
      (stats.processesMessages, attributes)
    }

    println(processesMessages)
    metrics.setStreamProcessedMessagesTotal(processesMessages)
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
      override def run(): Unit = registerWhenSystemIsInitialized(system)
    }).start()

  private def registerWhenSystemIsInitialized(system: akka.actor.ActorSystem): Unit =
    Retry.retryWithPrecondition(retryLimit, retryInterval)(system.isInitialized)(
      register(system)
    ) match {
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
