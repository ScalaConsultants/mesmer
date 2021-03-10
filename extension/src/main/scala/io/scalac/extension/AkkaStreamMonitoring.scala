package io.scalac.extension

import akka.actor.ActorRef
import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model.Tag.{ StageName, StreamName }
import io.scalac.core.model._
import io.scalac.extension.AkkaStreamMonitoring._
import io.scalac.extension.config.ConfigurationUtils._
import io.scalac.extension.event.ActorInterpreterStats
import io.scalac.extension.metric.MetricObserver.LazyResult
import io.scalac.extension.metric.StreamMetricMonitor.{ EagerLabels, Labels => GlobalLabels }
import io.scalac.extension.metric.StreamOperatorMetricsMonitor.Labels
import io.scalac.extension.metric.{ StreamMetricMonitor, StreamOperatorMetricsMonitor }
import io.scalac.extension.model._

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.jdk.DurationConverters._

object AkkaStreamMonitoring {

  sealed trait Command

  private case class StatsReceived(actorInterpreterStats: ActorInterpreterStats) extends Command

  case class StartStreamCollection(refs: Set[ActorRef]) extends Command

  private[AkkaStreamMonitoring] case object CollectionTimeout extends Command

  def apply(
    streamOperatorMonitor: StreamOperatorMetricsMonitor,
    streamMonitor: StreamMetricMonitor,
    node: Option[Node]
  ): Behavior[Command] =
    Behaviors.setup(ctx =>
      Behaviors.withTimers(scheduler =>
        new AkkaStreamMonitoring(ctx, streamOperatorMonitor, streamMonitor, scheduler, node)
      )
    )

  private final case class StageData(value: Long, direction: Direction, connectedWith: String)
  private final case class SnapshotEntry(stage: StageInfo, data: Option[StageData])
  private final case class IndexCacheEntry(indexes: Set[Int], distinctPorts: Boolean)

  private final case class StreamStats(streamName: StreamName, actors: Int, stages: Int, processesMessages: Long)
  private final class StreamStatsBuilder(val materializationName: StreamName) {
    private[this] var terminalName: Option[StageName] = None
    private[this] var processedMessages: Long         = 0
    private[this] var actors: Int                     = 0
    private[this] var stages: Int                     = 0

    def incActors(): this.type = {
      actors += 1
      this
    }

    def incStage(): this.type = {
      stages += 1
      this
    }

    def addStages(num: Int): this.type = {
      stages += 1
      this
    }

    def terminalName(stageName: StageName): this.type =
      if (terminalName.isEmpty) {
        this.terminalName = Some(stageName)
        this
      } else throw new IllegalStateException("Terminal name can be set once")

    def processedMessages(value: Long): this.type = {
      processedMessages = value
      this
    }

    def build: StreamStats = StreamStats(
      terminalName.fold(materializationName)(stage => StreamName(materializationName, stage)),
      actors,
      stages,
      processedMessages
    )

  }
}

class AkkaStreamMonitoring(
  ctx: ActorContext[Command],
  streamOperatorMonitor: StreamOperatorMetricsMonitor,
  streamMonitor: StreamMetricMonitor,
  scheduler: TimerScheduler[Command],
  node: Option[Node]
) extends AbstractBehavior[Command](ctx) {

  private val Timeout: FiniteDuration = streamCollectionTimeout

  private val indexCache: mutable.Map[StageInfo, IndexCacheEntry] = mutable.Map.empty
  private val operationsBoundMonitor                              = streamOperatorMonitor.bind()
  private val boundStreamMonitor                                  = streamMonitor.bind(EagerLabels(node))

  import ctx._

  private[this] val snapshot                = new AtomicReference[Option[Seq[SnapshotEntry]]](None)
  private[this] val globalProcessedSnapshot = new AtomicReference[Option[Seq[StreamStats]]](None)

  //append this only
  private[this] val localSnapshot: ListBuffer[SnapshotEntry]                      = ListBuffer.empty
  private[this] val localStreamStats: mutable.Map[StreamName, StreamStatsBuilder] = mutable.Map.empty

  boundStreamMonitor.streamProcessedMessages.setUpdater { result =>
    val streams = globalProcessedSnapshot.get()
    streams.foreach { statsSeq =>
      for {
        stats <- statsSeq
      } {
        val labels = GlobalLabels(node, stats.streamName)
        result.observe(stats.processesMessages, labels)
      }
    }
  }

  operationsBoundMonitor.processedMessages.setUpdater { result =>
    val state = snapshot.get()
    observeProcessed(result, state)
  }

  operationsBoundMonitor.operators.setUpdater { result =>
    val state = snapshot.get()
    observeOperators(result, state)
  }

  private val metricsAdapter = messageAdapter[ActorInterpreterStats](StatsReceived.apply)

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case StartStreamCollection(refs) if refs.nonEmpty =>
      log.debug("Start stream stats collection")
      scheduler.startSingleTimer(CollectionTimeout, CollectionTimeout, Timeout)

      refs.foreach { ref =>
        watch(ref)
        ref ! PushMetrics(metricsAdapter.toClassic)
      }
      collecting(refs)
    case StartStreamCollection(_) =>
      log.warn(s"StartStreamCollection with empty refs")
      this
    case StatsReceived(_) =>
      log.warn("Received stream running statistics after timeout")
      this
    case CollectionTimeout =>
      log.warn("[UNPLANNED SITUATION] CollectionTimeout on main behavior")
      this
  }

  def captureGlobalStats(): Unit = {
    boundStreamMonitor.runningStreamsTotal.setValue(localStreamStats.size)
    val values = localStreamStats.values.map(_.build).toSeq
    localStreamStats.clear()
    boundStreamMonitor.streamActorsTotal.setValue(values.foldLeft(0L)(_ + _.actors))

    globalProcessedSnapshot.set(Some(values))
  }

  private def createSnapshotEntry(stage: StageInfo, connectedWith: StageInfo, value: Long): SnapshotEntry =
    if (connectedWith ne null) {
      val connectedName = connectedWith.stageName
      SnapshotEntry(stage, Some(StageData(value, Direction.In, connectedName.name)))
    } else SnapshotEntry(stage, Some(StageData(value, Direction.In, "unknown"))) // TODO better handle case without name

  private def updateLocalState(
    stage: StageInfo,
    connections: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean = false
  ) =
    if (distinct) {
      // optimization for simpler graphs
      connections.foreach { conn =>
        localSnapshot.append(createSnapshotEntry(stage, stages(conn.out), conn.push))
      }
    } else {
      connections.groupBy(_.out).map { case (outIndex, connections) =>
        val value = connections.foldLeft(0L)(_ + _.push)
        localSnapshot.append(createSnapshotEntry(stage, stages(outIndex), value))
      }
    }

  private def swapState(): Unit = {
    snapshot.set(Some(localSnapshot.toSeq))
    localSnapshot.clear()
  }

  private def findWithIndex(
    stage: StageInfo,
    connections: Array[ConnectionStats]
  ): (Set[ConnectionStats], IndexCacheEntry) = {
    val indexSet: mutable.Set[Int]                   = mutable.Set.empty
    val connectionsSet: mutable.Set[ConnectionStats] = mutable.Set.empty
    val outputIndexSet: mutable.Set[Int]             = mutable.Set.empty
    var distinct                                     = true

    @tailrec
    def findInArray(index: Int): (Set[ConnectionStats], IndexCacheEntry) =
      if (index >= connections.length) (connectionsSet.toSet, IndexCacheEntry(indexSet.toSet, distinct))
      else {
        val connection = connections(index)
        if (connection.in == stage.id) {
          connectionsSet += connection
          indexSet += index
        }
        if (distinct) {
          if (outputIndexSet.contains(connection.out)) {
            distinct = false
          } else {
            outputIndexSet += connection.out
          }
        }
        findInArray(index + 1)
      }
    findInArray(0)
  }

  private def collecting(refs: Set[ActorRef]): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {
        case StatsReceived(ActorInterpreterStats(ref, subStreamName, shellInfo)) =>
          val refsLeft = refs - ref
          unwatch(ref)
          val streamStats =
            localStreamStats.getOrElseUpdate(subStreamName.streamName, new StreamStatsBuilder(subStreamName.streamName))
          streamStats.incActors()

          shellInfo.foreach { case (stageInfo, connections) =>
            for {
              stage <- stageInfo if stage ne null
            } {
              streamStats.incStage()

              val (stageConnections, distinct) = indexCache
                .get(stage)
                .fold {
                  val (wiredConnections, entry) = findWithIndex(stage, connections)
                  indexCache.put(stage, entry)
                  wiredConnections -> entry.distinctPorts
                }(entry => entry.indexes.map(connections.apply) -> entry.distinctPorts)

              if (stageConnections.nonEmpty)
                updateLocalState(stage, stageConnections, stageInfo, distinct)
              else localSnapshot.append(SnapshotEntry(stage, None))

              //set name for stream is it's terminal operator
              if (stage.terminal) {
                log.info("Found terminal stage {}", stage)
                streamStats.terminalName(stage.stageName.nameOnly)
                streamStats.processedMessages(stageConnections.foldLeft(0L)(_ + _.push))
              }
            }
          }

          if (refsLeft.isEmpty) {
            log.debug("Finished collecting stats")
            scheduler.cancel(CollectionTimeout)
            captureGlobalStats()
            swapState()
            this
          } else {
            collecting(refsLeft)
          }

        case CollectionTimeout =>
          log.warn("Collecting stats from running streams timeout")
          refs.foreach(ref => unwatch(ref))
          captureGlobalStats()
          swapState() // we record data gathered so far nevertheless
          this
        // TODO handle this case better
        case StartStreamCollection(_) =>
          log.warn("Another collection started but previous didn't finish")
          Behaviors.same
      }
      .receiveSignal(
        signalHandler
          .orElse[Signal, Behavior[Command]] { case Terminated(ref) =>
            log.debug("Stream ref {} terminated during metric collection", ref)
            collecting(refs - ref.toClassic)
          }
          .compose[(ActorContext[Command], Signal)] { case (_, signal) =>
            signal
          }
      )

  private def observeProcessed(result: LazyResult[Long, Labels], snapshot: Option[Seq[SnapshotEntry]]): Unit =
    snapshot.foreach(_.foreach {
      case SnapshotEntry(stageInfo, Some(StageData(value, direction, connectedWith))) =>
        val labels =
          Labels(
            stageInfo.stageName,
            stageInfo.subStreamName.streamName,
            stageInfo.terminal,
            node,
            Some(connectedWith -> direction)
          )
        result.observe(value, labels)
      case _ => // ignore sources as those will be covered by demand metrics
    })

  private def observeOperators(result: LazyResult[Long, Labels], snapshot: Option[Seq[SnapshotEntry]]): Unit =
    snapshot.foreach(_.groupBy(_.stage.subStreamName.streamName).foreach { case (streamName, snapshots) =>
      snapshots.groupBy(_.stage.stageName.nameOnly).foreach { case (stageName, elems) =>
        val labels = Labels(stageName, streamName, false, node, None)
        result.observe(elems.size, labels)
      }
    })

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = signalHandler

  private def signalHandler: PartialFunction[Signal, Behavior[Command]] = {
    case PreRestart =>
      operationsBoundMonitor.unbind()
      this
    case PostStop =>
      operationsBoundMonitor.unbind()
      this
  }

  private def streamCollectionTimeout: FiniteDuration =
    ctx.system.settings.config
      .tryValue("io.scalac.scalac.akka-monitoring.timeouts.query-region-stats")(_.getDuration)
      .map(_.toScala)
      .getOrElse(2.seconds)
}
