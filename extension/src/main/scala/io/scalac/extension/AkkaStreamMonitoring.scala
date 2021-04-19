package io.scalac.extension

import java.util
import java.util.concurrent.atomic.AtomicReference

import akka.actor.ActorRef
import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.TimerScheduler
import akka.actor.typed.scaladsl.adapter._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.event.Service.streamService
import io.scalac.core.event.StreamEvent
import io.scalac.core.event.StreamEvent.LastStreamStats
import io.scalac.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.core.model.Tag.StageName
import io.scalac.core.model.Tag.StreamName
import io.scalac.core.model._
import io.scalac.core.support.ModulesSupport
import io.scalac.extension.AkkaStreamMonitoring._
import io.scalac.extension.config.BufferConfig
import io.scalac.extension.config.CachingConfig
import io.scalac.extension.config.ConfigurationUtils._
import io.scalac.extension.metric.MetricObserver.Result
import io.scalac.extension.metric.StreamMetricMonitor
import io.scalac.extension.metric.StreamMetricMonitor.EagerLabels
import io.scalac.extension.metric.StreamMetricMonitor.{ Labels => GlobalLabels }
import io.scalac.extension.metric.StreamOperatorMetricsMonitor
import io.scalac.extension.metric.StreamOperatorMetricsMonitor.Labels

object AkkaStreamMonitoring {

  sealed trait Command

  private case class StatsReceived(actorInterpreterStats: StreamEvent) extends Command

  case class StartStreamCollection(refs: Set[ActorRef]) extends Command

  private[extension] case object CollectionTimeout extends Command

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

  private[extension] final case class StageData(value: Long, connectedWith: String)
  private[extension] final case class SnapshotEntry(stage: StageInfo, data: Option[StageData])
  private[extension] final case class DirectionData(stats: Set[ConnectionStats], distinct: Boolean)
  private[extension] final case class IndexData(input: DirectionData, output: DirectionData)

  private[extension] final case class StreamStats(
    streamName: StreamName,
    actors: Int,
    stages: Int,
    processesMessages: Long
  )
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

  private[extension] final class ConnectionsIndexCache private (
    private[extension] val indexCache: mutable.Map[StageInfo, ConnectionsIndexCache.IndexCacheEntry]
  ) {
    import ConnectionsIndexCache._

    def get(stage: StageInfo)(connections: Array[ConnectionStats]): IndexData = indexCache
      .get(stage)
      .fold {
        val (wiredInputs, wiredOutputs, entry) = findWithIndex(stage, connections)
        indexCache.put(stage, entry)
        IndexData(DirectionData(wiredInputs, entry.distinctInputs), DirectionData(wiredOutputs, entry.distinctOutputs))
      }(entry =>
        IndexData(
          DirectionData(entry.inputs.map(connections.apply), entry.distinctInputs),
          DirectionData(entry.outputs.map(connections.apply), entry.distinctOutputs)
        )
      )

    private def findWithIndex(
      stage: StageInfo,
      connections: Array[ConnectionStats]
    ): (Set[ConnectionStats], Set[ConnectionStats], IndexCacheEntry) = {
      val inputIndexSet: mutable.Set[Int]                    = mutable.Set.empty
      val outputIndexSet: mutable.Set[Int]                   = mutable.Set.empty
      val inputConnectionsSet: mutable.Set[ConnectionStats]  = mutable.Set.empty
      val outputConnectionsSet: mutable.Set[ConnectionStats] = mutable.Set.empty

      val inputOutputIds = mutable.Set.empty[Int]
      val outputInputIds = mutable.Set.empty[Int]

      var distinctOutput = true
      var distinctInput  = true

      @tailrec
      def findInArray(index: Int): (Set[ConnectionStats], Set[ConnectionStats], IndexCacheEntry) =
        if (index >= connections.length)
          (
            inputConnectionsSet.toSet,
            outputConnectionsSet.toSet,
            IndexCacheEntry(inputIndexSet.toSet, outputIndexSet.toSet, distinctInput, distinctOutput)
          )
        else {
          val connection = connections(index)
          if (connection.in == stage.id) {
            inputConnectionsSet += connection
            inputIndexSet += index

            if (distinctInput) {
              if (inputOutputIds.contains(connection.out)) {
                distinctInput = false
              } else {
                inputOutputIds += connection.out
              }
            }

          } else if (connection.out == stage.id) {
            outputConnectionsSet += connection
            outputIndexSet += index

            if (distinctOutput) {
              if (outputInputIds.contains(connection.in)) {
                distinctOutput = false
              } else {
                outputInputIds += connection.in
              }
            }

          }
          findInArray(index + 1)
        }
      findInArray(0)
    }
  }

  object ConnectionsIndexCache {
    private[extension] final case class IndexCacheEntry(
      inputs: Set[Int],
      outputs: Set[Int],
      distinctInputs: Boolean,
      distinctOutputs: Boolean
    )

    private[extension] def bounded(entries: Int): ConnectionsIndexCache = {

      val mutableMap: mutable.Map[StageInfo, IndexCacheEntry] =
        new util.LinkedHashMap[StageInfo, IndexCacheEntry](entries, 0.75f, true) {
          override def removeEldestEntry(eldest: util.Map.Entry[StageInfo, IndexCacheEntry]): Boolean =
            this.size() >= entries
        }.asScala

      new ConnectionsIndexCache(mutableMap)
    }

    /**
     * Exists solely for testing purpose
     * @return
     */
    private[extension] def empty = new ConnectionsIndexCache(mutable.Map.empty)

  }
}

class AkkaStreamMonitoring(
  ctx: ActorContext[Command],
  streamOperatorMonitor: StreamOperatorMetricsMonitor,
  streamMonitor: StreamMetricMonitor,
  scheduler: TimerScheduler[Command],
  node: Option[Node]
) extends AbstractBehavior[Command](ctx) {
  import ModulesSupport._

  private val Timeout: FiniteDuration = streamCollectionTimeout

  private val cachingConfig          = CachingConfig.fromConfig(ctx.system.settings.config, akkaStreamModule)
  private val bufferConfig           = BufferConfig.fromConfig(ctx.system.settings.config, akkaStreamModule)
  private val indexCache             = ConnectionsIndexCache.bounded(cachingConfig.maxEntries)
  private val operationsBoundMonitor = streamOperatorMonitor.bind()
  private val boundStreamMonitor     = streamMonitor.bind(EagerLabels(node))

  import ctx._

  private[this] val processedSnapshot       = new AtomicReference[Option[Seq[SnapshotEntry]]](None)
  private[this] val demandSnapshot          = new AtomicReference[Option[Seq[SnapshotEntry]]](None)
  private[this] val globalProcessedSnapshot = new AtomicReference[Option[Seq[StreamStats]]](None)

  //append this only
  private[this] val localProcessedSnapshot = ListBuffer.empty[SnapshotEntry]
  private[this] val localDemandSnapshot    = ListBuffer.empty[SnapshotEntry]
  private[this] val localStreamStats       = mutable.Map.empty[StreamName, StreamStatsBuilder]

  private def init(): Unit = {
    system.receptionist ! Register(streamService.serviceKey, messageAdapter[StreamEvent](StatsReceived.apply))

    boundStreamMonitor.streamProcessedMessages.setUpdater { result =>
      val streams = globalProcessedSnapshot.get()
      streams.foreach { statsSeq =>
        for (stats <- statsSeq) {
          val labels = GlobalLabels(node, stats.streamName)
          result.observe(stats.processesMessages, labels)
        }
      }
    }

    operationsBoundMonitor.processedMessages.setUpdater { result =>
      val state = processedSnapshot.get()
      observeSnapshot(result, state)
    }

    operationsBoundMonitor.operators.setUpdater { result =>
      val state = processedSnapshot.get()
      observeOperators(result, state)
    }

    operationsBoundMonitor.demand.setUpdater { result =>
      val state = demandSnapshot.get()
      observeSnapshot(result, state)
    }
  }

  def onMessage(msg: Command): Behavior[Command] =
    Behaviors.withStash(bufferConfig.size) { buffer =>
      msg match {
        case StartStreamCollection(refs) if refs.nonEmpty =>
          log.debug("Start stream stats collection")
          scheduler.startSingleTimer(CollectionTimeout, CollectionTimeout, Timeout)

          refs.foreach { ref =>
            watch(ref)
            ref ! PushMetrics
          }
          buffer.unstashAll(collecting(refs))
        case StartStreamCollection(_) =>
          log.warn("StartStreamCollection with empty refs")
          this
        case stats @ StatsReceived(_: LastStreamStats) =>
          log.debug("Received last stats for shell")
          buffer.stash(stats)
          Behaviors.same
        case StatsReceived(_) =>
          log.warn("Received stream running statistics after timeout")
          this
        case CollectionTimeout =>
          log.warn("[UNPLANNED SITUATION] CollectionTimeout on main behavior")
          this
      }
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
      SnapshotEntry(stage, Some(StageData(value, connectedName.name)))
    } else SnapshotEntry(stage, Some(StageData(value, "unknown"))) // TODO better handle case without name

  private def updateLocalProcessedState(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean = false
  ): Unit =
    updateLocalState(stage, connectionStats, stages, distinct, conn => (conn.out, conn.push), localProcessedSnapshot)

  private def updateLocalDemandState(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean = false
  ): Unit =
    updateLocalState(stage, connectionStats, stages, distinct, conn => (conn.in, conn.pull), localDemandSnapshot)

  private def updateLocalState(
    stage: StageInfo,
    connectionStats: Set[ConnectionStats],
    stages: Array[StageInfo],
    distinct: Boolean,
    extractFunction: ConnectionStats => (Int, Long),
    localState: ListBuffer[SnapshotEntry]
  ): Unit =
    if (connectionStats.nonEmpty) {
      if (distinct) {
        // optimization for simpler graphs
        connectionStats.foreach { conn =>
          val (out, push) = extractFunction(conn)
          localState.append(createSnapshotEntry(stage, stages(out), push))
        }
      } else {
        connectionStats.map(extractFunction).groupBy(_._1).map { case (index, connections) =>
          val value = connections.foldLeft(0L)(_ + _._2)
          localState.append(createSnapshotEntry(stage, stages(index), value))
        }
      }
    } else {
      localState.append(SnapshotEntry(stage, None))
    }

  private def swapState(): Unit = {
    processedSnapshot.set(Some(localProcessedSnapshot.toSeq))
    demandSnapshot.set(Some(localDemandSnapshot.toSeq))
    localProcessedSnapshot.clear()
    localDemandSnapshot.clear()
  }

  private def collecting(refs: Set[ActorRef]): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {
        case StatsReceived(event) =>
          val (refsLeft, subStreamName, shellInfo) = event match {
            case StreamInterpreterStats(ref, subStreamName, shellInfo) =>
              unwatch(ref)
              (refs - ref, subStreamName, shellInfo)
            case LastStreamStats(_, subStreamName, shellInfo) =>
              (refs, subStreamName, Set(shellInfo))
          }

          val streamStats =
            localStreamStats.getOrElseUpdate(subStreamName.streamName, new StreamStatsBuilder(subStreamName.streamName))
          streamStats.incActors()

          shellInfo.foreach { case (stageInfo, connections) =>
            for {
              stage <- stageInfo if stage ne null
            } {
              streamStats.incStage()

              val IndexData(DirectionData(inputStats, inputDistinct), DirectionData(outputStats, outputDistinct)) =
                indexCache.get(stage)(connections)

              updateLocalProcessedState(stage, inputStats, stageInfo, inputDistinct)
              updateLocalDemandState(stage, outputStats, stageInfo, outputDistinct)

              //set name for stream is it's terminal operator
              if (stage.terminal) {
                log.info("Found terminal stage {}", stage)
                streamStats.terminalName(stage.stageName.nameOnly)
                streamStats.processedMessages(inputStats.foldLeft(0L)(_ + _.push))
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

  private def observeSnapshot(result: Result[Long, Labels], snapshot: Option[Seq[SnapshotEntry]]): Unit =
    snapshot.foreach(_.foreach {
      case SnapshotEntry(stageInfo, Some(StageData(value, connectedWith))) =>
        val labels =
          Labels(
            stageInfo.stageName,
            stageInfo.subStreamName.streamName,
            stageInfo.terminal,
            node,
            Some(connectedWith)
          )
        result.observe(value, labels)
      case _ => // ignore metrics without data
    })

  private def observeOperators(result: Result[Long, Labels], snapshot: Option[Seq[SnapshotEntry]]): Unit =
    snapshot.foreach(_.groupBy(_.stage.subStreamName.streamName).foreach { case (streamName, snapshots) =>
      snapshots.groupBy(_.stage.stageName.nameOnly).foreach { case (stageName, elems) =>
        val labels = Labels(stageName, streamName, terminal = false, node, None)
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

  init()
}
