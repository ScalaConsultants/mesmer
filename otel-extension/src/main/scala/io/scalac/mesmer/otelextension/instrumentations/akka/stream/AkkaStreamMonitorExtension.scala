package io.scalac.mesmer.otelextension.instrumentations.akka.stream

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
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.core.event.Service.streamService
import io.scalac.mesmer.core.event.StreamEvent
import io.scalac.mesmer.core.event.StreamEvent.LastStreamStats
import io.scalac.mesmer.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.mesmer.core.model.StreamInfo
import io.scalac.mesmer.core.model.Tag.StreamName
import io.scalac.mesmer.core.model.stream._
import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.core.util.ClassicActorSystemOps.ActorSystemOps
import io.scalac.mesmer.core.util.Retry
import io.scalac.mesmer.core.util.TypedActorSystemOps.{ ActorSystemOps => TypedActorSystemOps }
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMetrics.isTerminalStageKey
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMetrics.stageNameAttributeKey
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMetrics.streamNameAttributeKey
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension.StreamStatsReceived
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension.log

final class AkkaStreamMonitorExtension(
  actorSystem: ActorSystem[_],
  streamSnapshotService: StreamSnapshotsService,
  metrics: AkkaStreamMetrics,
  indexCache: ConnectionsIndexCache
) extends Extension {

  private lazy val nodeAttribute: Attributes = AkkaStreamAttributes.forNode(actorSystem.clusterNodeName)

  private val interval: FiniteDuration = AkkaStreamConfig.metricSnapshotRefreshInterval(actorSystem.classicSystem)

  def start(): Behavior[StreamStatsReceived] = Behaviors.setup[StreamStatsReceived] { ctx =>
    actorSystem.receptionist ! Register(
      streamService.serviceKey,
      ctx.messageAdapter[StreamEvent](StreamStatsReceived.apply)
    )
    actorSystem.scheduler.scheduleWithFixedDelay(interval, interval)(() => collectStreamMetrics())(
      actorSystem.executionContext
    )

    Behaviors.receiveMessage[StreamStatsReceived] {
      case StreamStatsReceived(StreamInterpreterStats(ref, streamName, shellInfo)) =>
        streamSnapshotService.loadInfo(ref, StreamInfo(streamName, shellInfo))
        Behaviors.same
      case StreamStatsReceived(LastStreamStats(ref, streamName, shellInfo)) =>
        streamSnapshotService.loadInfo(ref, StreamInfo(streamName, Set(shellInfo)))
        Behaviors.same
    }
  }

  private def collectStreamMetrics(): Unit = {
    val currentSnapshot    = streamSnapshotService.getSnapshot()
    val currentStreamStats = mutable.Map.empty[StreamName, StreamStatsBuilder]

    val streamStats: Seq[StreamStats] = currentSnapshot.values.map { streamInfo =>
      val subStreamName = streamInfo.subStreamName
      val stats: StreamStatsBuilder =
        currentStreamStats.getOrElseUpdate(subStreamName.streamName, new StreamStatsBuilder(subStreamName.streamName))
      stats.incActors()
      collectPerStreamMetrics(stats, streamInfo)
      stats
    }.map(_.build).toSeq

    setRunningActorsTotal(streamStats)
    metrics.setRunningStreamsTotal(currentStreamStats.size, nodeAttribute)
    setStreamProcessedMessages(streamStats)
  }

  private def collectPerStreamMetrics(stats: StreamStatsBuilder, streamInfo: StreamInfo): Unit =
    streamInfo.shellInfo.foreach { case (stageInfo, connections) =>
      for {
        stage <- stageInfo if stage ne null
      } {
        stats.incStage()

        val IndexData(DirectionData(inputStats, inputDistinct), DirectionData(outputStats, outputDistinct)) =
          indexCache.get(stage)(connections)

        val operatorsPerStage = getPerStageValues(getProcessedState(stage, inputStats, stageInfo, inputDistinct))

        val demandPerStage = getPerStageValues(getDemandState(stage, outputStats, stageInfo, outputDistinct))

        metrics.setRunningOperators(operatorsPerStage)
        metrics.setOperatorDemand(demandPerStage)

        if (stage.terminal) {
          log.debug("Found terminal stage {}", stage)
          stats.terminalName(stage.stageName.nameOnly)
          stats.processedMessages(inputStats.foldLeft(0L)(_ + _.push))
        }
      }
    }

  private def setRunningActorsTotal(streamStats: Seq[StreamStats]): Unit = {
    val actorsPerStream: Seq[(Long, Attributes)] = streamStats
      .map(stats => (stats.actors, stats.streamName))
      .map { case (actors, streamName) =>
        (actors.toLong, nodeAttribute.toBuilder.put(streamNameAttributeKey, streamName.name).build())
      }

    metrics.setRunningActorsTotal(actorsPerStream)
  }
  private def setStreamProcessedMessages(streamStats: Seq[StreamStats]): Unit = {
    val builder = nodeAttribute.toBuilder

    val processesMessages = streamStats.map { stats =>
      val attributes = builder.put(streamNameAttributeKey, stats.streamName.name).build()
      (stats.processesMessages, attributes)
    }

    metrics.setStreamProcessedMessagesTotal(processesMessages)
  }

  private def getPerStageValues(snapshot: Seq[SnapshotEntry]): Seq[(Long, Attributes)] =
    snapshot
      .groupBy(_.stage.subStreamName.streamName)
      .flatMap { case (streamName, entriesPerStream) =>
        entriesPerStream.groupBy(_.stage.stageName.nameOnly).map { case (stageName, entriesPerStage) =>
          val attributes = nodeAttribute.toBuilder
            .put(stageNameAttributeKey, stageName.name)
            .put(streamNameAttributeKey, streamName.name)
            .put(isTerminalStageKey, "false")
            .build()
          val value = entriesPerStage.size
          (value.toLong, attributes)
        }
      }
      .toSeq

  private def getDemandState(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean = false
  ): Seq[SnapshotEntry] = computeSnapshotEntries(stage, connectionStats, stages, distinct, conn => (conn.in, conn.pull))

  private def getProcessedState(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean = false
  ): Seq[SnapshotEntry] =
    computeSnapshotEntries(stage, connectionStats, stages, distinct, conn => (conn.out, conn.push))

  private def computeSnapshotEntries(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean,
    extractFunction: ConnectionStats => (Int, Long)
  ): Seq[SnapshotEntry] =
    if (connectionStats.nonEmpty) {
      if (distinct) {
        // optimization for simpler graphs
        connectionStats.map { conn =>
          val (out, push) = extractFunction(conn)
          createSnapshotEntry(stage, stages(out), push)
        }.toSeq
      } else {
        connectionStats
          .map(extractFunction)
          .groupBy(_._1)
          .map { case (index, connections) =>
            val value = connections.foldLeft(0L)(_ + _._2)
            createSnapshotEntry(stage, stages(index), value)
          }
          .toSeq
      }
    } else {
      Seq(SnapshotEntry(stage, None))
    }

  private def createSnapshotEntry(stage: StageInfo, connectedWith: StageInfo, value: Long): SnapshotEntry =
    if (connectedWith ne null) {
      val connectedName = connectedWith.stageName
      SnapshotEntry(stage, Some(StageData(value, connectedName.name)))
    } else SnapshotEntry(stage, Some(StageData(value, "unknown")))

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
    system,
    StreamSnapshotsService.make(),
    new AkkaStreamMetrics(system),
    ConnectionsIndexCache.bounded(CachingConfig.fromConfig(system.settings.config, AkkaStreamModule).maxEntries)
  )
}
