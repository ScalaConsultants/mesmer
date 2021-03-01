package io.scalac.extension

import akka.actor.ActorRef
import akka.actor.typed._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model.Tag.StreamName
import io.scalac.core.model._
import io.scalac.extension.AkkaStreamMonitoring._
import io.scalac.extension.event.ActorInterpreterStats
import io.scalac.extension.metric.MetricObserver.LazyResult
import io.scalac.extension.metric.StreamMetricMonitor.{ Labels => GlobalLabels }
import io.scalac.extension.metric.StreamOperatorMetricsMonitor.Labels
import io.scalac.extension.metric.{ StreamMetricMonitor, StreamOperatorMetricsMonitor }
import io.scalac.extension.model.Direction._
import io.scalac.extension.model._

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.{ FiniteDuration, _ }

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
}

class AkkaStreamMonitoring(
  ctx: ActorContext[Command],
  streamOperatorMonitor: StreamOperatorMetricsMonitor,
  streamMonitor: StreamMetricMonitor,
  scheduler: TimerScheduler[Command],
  node: Option[Node],
  timeout: FiniteDuration = 2.seconds
) extends AbstractBehavior[Command](ctx) {

  val indexCache: mutable.Map[StageInfo, Set[Int]] = mutable.Map.empty
  private val operationsBoundMonitor               = streamOperatorMonitor.bind()
  private val boundStreamMonitor                   = streamMonitor.bind(GlobalLabels(node))

  import ctx._

  private[this] val snapshot       = new AtomicReference[Option[Seq[SnapshotEntry]]](None)
  private[this] val runningActors  = new AtomicReference[Option[Int]](None)
  private[this] val runningStreams = new AtomicReference[Option[Int]](None)

  boundStreamMonitor.runningStreams.setUpdater { result =>
    val streams = runningStreams.get()
    streams.foreach(value => result.observe(value)) // if none no result is set
  }

  boundStreamMonitor.streamActors.setUpdater { result =>
    val actors = runningActors.get()
    actors.foreach(value => result.observe(value)) // if none no result is set
  }

  operationsBoundMonitor.processedMessages.setUpdater { result =>
    val state = snapshot.get()
    observeProcessed(result, state)
  }

  operationsBoundMonitor.operators.setUpdater { result =>
    val state = snapshot.get()
    observeOperators(result, state)
  }

  private[this] val connectionGraph: mutable.Map[StageInfo, Set[ConnectionStats]] =
    mutable.Map.empty

  private val metricsAdapter = messageAdapter[ActorInterpreterStats](StatsReceived.apply)

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case StartStreamCollection(refs) if refs.nonEmpty =>
      log.info("Start stream stats collection")
      scheduler.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

      refs.foreach { ref =>
        watch(ref)
        ref ! PushMetrics(metricsAdapter.toClassic)
      }
      collecting(refs, Set.empty)
    case StartStreamCollection(_) =>
      log.error(s"StartStreamCollection with empty refs")
      this
    case StatsReceived(_) =>
      log.warn("Received stream running statistics after timeout")
      this
  }

  def captureGlobalStats(names: Set[StreamName]): Unit = {
    val actors  = names.size
    val streams = names.map(_.name).size
    runningActors.set(Some(actors))
    runningStreams.set(Some(streams))
  }

  // TODO optimize this!
  def captureState(): Unit =
    this.snapshot.set(Some(connectionGraph.flatMap {
      case (info, in) if in.nonEmpty =>
        in.groupBy(_.outName).map {
          case (upstream, connections) =>
            val push = connections.foldLeft(0L)(_ + _.push)
            val data = StageData(push, In, upstream.name)
            SnapshotEntry(info, Some(data))
        }
      case (info, _) => Seq(SnapshotEntry(info, None)) // takes into account sources
    }.toSeq))

  private def findWithIndex(stage: StageInfo, connections: Array[ConnectionStats]): (Set[ConnectionStats], Set[Int]) = {
    val indexSet: mutable.Set[Int]                   = mutable.Set.empty
    val connectionsSet: mutable.Set[ConnectionStats] = mutable.Set.empty
    @tailrec
    def findInArray(index: Int): (Set[ConnectionStats], Set[Int]) =
      if (index >= connections.length) (connectionsSet.toSet, indexSet.toSet)
      else {
        val connection = connections(index)
        if (connection.inName == stage.stageName) {
          connectionsSet += connection
          indexSet += index
        }
        findInArray(index + 1)
      }
    findInArray(0)
  }

  def collecting(refs: Set[ActorRef], names: Set[StreamName]): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {
        case StatsReceived(ActorInterpreterStats(ref, streamName, shellInfo)) =>
          val refsLeft = refs - ref

          unwatch(ref)

          val stages = (for {
            (stagesInfo, connections) <- shellInfo
            stage                     <- stagesInfo
          } yield {
            val stageConnections = indexCache
              .get(stage)
              .fold {
                val (wiredConnections, indexes) = findWithIndex(stage, connections)
                indexCache.put(stage, indexes)
                wiredConnections
              }(indexes => indexes.map(connections.apply))
            stage -> stageConnections
          }).toMap

          connectionGraph ++= stages

          if (refsLeft.isEmpty) {
            log.debug("Finished collecting stats")
            scheduler.cancel(CollectionTimeout)
            captureGlobalStats(names + streamName)
            captureState()
            this
          } else {
            collecting(refsLeft, names + streamName)
          }

        case CollectionTimeout =>
          log.warn("Collecting stats from running streams timeout")
          refs.foreach(ref => unwatch(ref))
          captureGlobalStats(names)
          captureState() // we record data gathered so far nevertheless
          this
        // TODO handle this case better
        case StartStreamCollection(_) =>
          log.debug("Another collection started but previous didn't finish")
          Behaviors.same
      }
      .receiveSignal(
        signalHandler
          .orElse[Signal, Behavior[Command]] {
            case Terminated(ref) =>
              log.debug("Stream ref {} terminated during metric collection", ref)
              collecting(refs - ref.toClassic, names)
          }
          .compose[(ActorContext[Command], Signal)] {
            case (_, signal) => signal
          }
      )

  private def observeProcessed(result: LazyResult[Long, Labels], snapshot: Option[Seq[SnapshotEntry]]): Unit =
    snapshot.foreach(_.foreach {
      case SnapshotEntry(stageInfo, Some(StageData(value, direction, connectedWith))) =>
        val labels =
          Labels(stageInfo.stageName, node, Some(stageInfo.streamName.name), Some(connectedWith -> direction))
        result.observe(value, labels)
      case _ => // ignore sources as those will be covered by demand metrics
    })

  private def observeOperators(result: LazyResult[Long, Labels], snapshot: Option[Seq[SnapshotEntry]]): Unit =
    snapshot.foreach(_.groupBy(_.stage.streamName.name).foreach {
      case (streamName, snapshots) =>
        snapshots.groupBy(_.stage.stageName.nameOnly).foreach {
          case (stageName, elems) =>
            val labels = Labels(stageName, node, Some(streamName), None)
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
}
