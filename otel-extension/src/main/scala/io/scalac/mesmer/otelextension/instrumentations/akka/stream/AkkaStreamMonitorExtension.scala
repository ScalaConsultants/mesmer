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

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.core.event.Service.streamService
import io.scalac.mesmer.core.event.StreamEvent
import io.scalac.mesmer.core.event.StreamEvent.LastStreamStats
import io.scalac.mesmer.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.model.StreamInfo
import io.scalac.mesmer.core.model.Tag._
import io.scalac.mesmer.core.model.stream._
import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.core.util.ClassicActorSystemOps.ActorSystemOps
import io.scalac.mesmer.core.util.Retry
import io.scalac.mesmer.core.util.TypedActorSystemOps.{ ActorSystemOps => TypedActorSystemOps }
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension.StreamStatsReceived

final class AkkaStreamMonitorExtension(
  actorSystem: ActorSystem[_],
  streamSnapshotService: StreamSnapshotsService,
  metrics: AkkaStreamMetrics,
  indexCache: ConnectionsIndexCache
) extends Extension {

  private lazy val nodeName: Option[Node] = actorSystem.clusterNodeName

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
    val currentSnapshot = streamSnapshotService.getSnapshot()

    val currentlyRunningStreams: Map[String, Map[ActorRef, StreamInfo]] =
      currentSnapshot.groupBy(_._2.subStreamName.streamName.name)

    val stageSnapshots    = currentSnapshot.values.flatMap(collectStageSnapshots)
    val operatorDemand    = stageSnapshots.flatMap(snapshot => getPerStageValues(snapshot.output)).toMap
    val runningOperators  = stageSnapshots.flatMap(snapshot => getPerStageOperatorValues(snapshot.input)).toMap
    val processedMessages = stageSnapshots.flatMap(snapshot => getPerStageValues(snapshot.input)).toMap

    val nodeAttribute = AkkaStreamAttributes.forNode(nodeName)
    metrics.setRunningStreamsTotal(currentlyRunningStreams.size, nodeAttribute)
    metrics.setRunningActorsTotal(currentSnapshot.size, nodeAttribute)
    metrics.setOperatorDemand(operatorDemand)
    metrics.setRunningOperators(runningOperators)
    metrics.setStreamProcessedMessagesTotal(processedMessages)
  }

  private case class StageSnapshot(stage: StageInfo, input: Seq[SnapshotEntry], output: Seq[SnapshotEntry])

  private def collectStageSnapshots(streamInfo: StreamInfo): Set[StageSnapshot] =
    streamInfo.shellInfo.collect { case (stageInfo, connections) =>
      stageInfo
        .withFilter(stage => stage ne null)
        .map { stage =>
          val indexData: IndexData = indexCache.get(stage)(connections)

          // processed messages & operators
          val inputSnapshot = computeSnapshotEntries(
            stage,
            indexData.input.stats,
            stageInfo,
            indexData.input.distinct,
            conn => (conn.out, conn.push)
          )

          // demand
          val outputSnapshot = computeSnapshotEntries(
            stage,
            indexData.output.stats,
            stageInfo,
            indexData.output.distinct,
            conn => (conn.in, conn.pull)
          )

          StageSnapshot(stage, inputSnapshot, outputSnapshot)
        }
    }.flatten

  private def getPerStageOperatorValues(snapshot: Seq[SnapshotEntry]): Map[Attributes, Long] =
    snapshot
      .groupBy(_.stage.subStreamName.streamName)
      .flatMap { case (streamName, entriesPerStream) =>
        entriesPerStream.groupBy(_.stage.stageName.nameOnly).map { case (stageName, entriesPerStage) =>
          val attributes = StageAttributes(
            stageName,
            streamName,
            terminal = false,
            nodeName,
            None
          )

          asOtelAttributes(attributes) -> entriesPerStage.size.toLong
        }
      }

  private def getPerStageValues(snapshot: Seq[SnapshotEntry]): Map[Attributes, Long] =
    snapshot.collect { case SnapshotEntry(stageInfo, Some(StageData(value, connectedWith))) =>
      val attributes = StageAttributes(
        stageInfo.stageName,
        stageInfo.subStreamName.streamName,
        stageInfo.terminal,
        nodeName,
        Some(connectedWith)
      )

      asOtelAttributes(attributes) -> value
    }.toMap

  private def asOtelAttributes(attributes: StageAttributes): Attributes = {
    val builder = Attributes.builder()
    attributes.serialize.foreach { case (k, v) => builder.put(k, v) }
    builder.build()
  }

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
