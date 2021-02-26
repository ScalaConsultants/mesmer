package io.scalac.extension

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed._
import akka.actor.{ typed, ActorRef }
import akka.util.Timeout
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model._
import io.scalac.core.util.stream.streamNameFromActorRef
import io.scalac.extension.AkkaStreamMonitoring._
import io.scalac.extension.event.ActorInterpreterStats
import io.scalac.extension.metric.MetricObserver.LazyResult
import io.scalac.extension.metric.StreamMetricMonitor.{ Labels => GlobalLabels }
import io.scalac.extension.metric.StreamOperatorMetricsMonitor.Labels
import io.scalac.extension.metric.{ StreamMetricMonitor, StreamOperatorMetricsMonitor }
import io.scalac.extension.model.Direction._
import io.scalac.extension.model._

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.{ FiniteDuration, _ }

object AkkaStreamMonitoring {

  sealed trait Command

  private case class StatsReceived(actorInterpreterStats: ActorInterpreterStats) extends Command

  case class StartStreamCollection(refs: Set[ActorRef]) extends Command

  private case class CollectProcessed(resultCallback: LazyResult[Long, Labels], replyTo: typed.ActorRef[Unit])
      extends Command

  private case class CollectOperators(resultCallback: LazyResult[Long, Labels], replyTo: typed.ActorRef[Unit])
      extends Command

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

  private case class SnapshotEntry(stage: StageInfo, value: Long, direction: Direction, connectedWith: String)

  //stage info name should be unique across same running actor
  private case class UniqueStageInfo(ref: ActorRef, info: StageInfo)

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
  val operationsBoundMonitor                       = streamOperatorMonitor.bind()

  import ctx._

  implicit lazy val system: typed.ActorSystem[Nothing] = ctx.system
  implicit lazy val executionContext                   = system.executionContext
  implicit lazy val _timeout: Timeout                  = 2.seconds

  operationsBoundMonitor.processedMessages
    .setUpdater(result => Await.result(self.ask[Unit](ref => CollectProcessed(result, ref)), 1.second)) // really not cool way to do it

  operationsBoundMonitor.operators
    .setUpdater(result => Await.result(self.ask[Unit](ref => CollectOperators(result, ref)), 1.second)) // really not cool way to do it

  private[this] val connectionGraph: mutable.Map[StageInfo, Set[ConnectionStats]] =
    mutable.Map.empty
  private[this] val globalStreamMonitor = streamMonitor.bind(GlobalLabels(node))

  private[this] var stageSnapshot: Option[Seq[SnapshotEntry]] = None

  system.receptionist ! Register(streamServiceKey, messageAdapter(StatsReceived.apply))

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case StartStreamCollection(refs) if refs.nonEmpty =>
      log.info("Start stream stats collection")
      scheduler.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

      updateRunningStreams(refs)
      refs.foreach { ref =>
        watch(ref)
        ref ! PushMetrics
      }
      collecting(refs)
    case StartStreamCollection(_) =>
      log.error(s"StartStreamCollection with empty refs")
      this
    case StatsReceived(_) =>
      log.warn("Received stream running statistics after timeout")
      this
    case CollectProcessed(result, ref) =>
      observeProcessed(result, ref)
      this
    case CollectOperators(result, ref) =>
      observeOperators(result, ref)
      this

  }

  def observeProcessed(result: LazyResult[Long, Labels], ref: typed.ActorRef[Unit]): Unit = {
    log.debug("Reporting processed messages")
    stageSnapshot.foreach(_.foreach {
      case SnapshotEntry(stageInfo, value, direction, connectedWith) =>
        log.trace("Reporting data of stage {}", stageInfo)
        val labels =
          Labels(stageInfo.stageName, node, Some(stageInfo.streamName.name), Some(connectedWith -> direction))
        result.observe(value, labels)
    })
    ref ! ()
  }

  def observeOperators(result: LazyResult[Long, Labels], ref: typed.ActorRef[Unit]): Unit = {
    log.debug("Reporting running operators")
    stageSnapshot.foreach(_.groupBy(_.stage.streamName).foreach {
      case (streamName, snapshots) =>
        snapshots.groupBy(_.stage.stageName).foreach {
          case (stageName, elems) =>
            val labels = Labels(stageName, node, Some(streamName.name), None)
            log.info("Report operator for {} - {}", stageName, elems.size)
            result.observe(elems.size, labels)
        }
    })
    ref ! ()
  }

  def updateRunningStreams(refs: Set[ActorRef]): Unit = {

    globalStreamMonitor.streamActors.setValue(refs.size)
    val streams = refs.map(streamNameFromActorRef).size
    globalStreamMonitor.runningStreams.setValue(streams)
  }

  // TODO optimize this!
  def captureState(): Unit =
    this.stageSnapshot = Some(connectionGraph.flatMap {
      case (info, in) =>
        in.groupBy(_.outName).map {
          case (upstream, connections) =>
            val push = connections.foldLeft(0L)(_ + _.push)
            SnapshotEntry(info, push, In, upstream.name)
        }
    }.toSeq)

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

  def collecting(refs: Set[ActorRef]): Behavior[Command] =
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
            captureState()
            this
          } else {
            collecting(refsLeft)
          }

        case CollectionTimeout =>
          log.warn("Collecting stats from running streams timeout")
          refs.foreach(ref => unwatch(ref))
          captureState() // we record data gathered so far nevertheless
          this
        // TODO handle this case better
        case StartStreamCollection(_) =>
          log.debug("Another collection started but previous didn't finish")
          Behaviors.same
        case CollectProcessed(result, ref) =>
          observeProcessed(result, ref)
          Behaviors.same
        case CollectOperators(result, ref) =>
          observeOperators(result, ref)
          this
      }
      .receiveSignal {
        case (_, Terminated(ref)) =>
          log.debug("Stream ref {} terminated", ref)
          collecting(refs - ref.toClassic)

      }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PreRestart =>
      operationsBoundMonitor.unbind()
      this
    case PostStop =>
      operationsBoundMonitor.unbind()
      this
  }
}
