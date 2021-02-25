package io.scalac.extension

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed.{ Behavior, Terminated }
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

}

case class SnapshotEntry(stage: UniqueStageInfo, value: Long, direction: Direction, connectedWith: String)

//stage info name should be unique across same running actor
case class UniqueStageInfo(ref: ActorRef, info: StageInfo)

class AkkaStreamMonitoring(
  ctx: ActorContext[Command],
  streamOperatorMonitor: StreamOperatorMetricsMonitor,
  streamMonitor: StreamMetricMonitor,
  scheduler: TimerScheduler[Command],
  node: Option[Node],
  timeout: FiniteDuration = 2.seconds
) extends AbstractBehavior[Command](ctx) {

  import ctx._

  implicit lazy val system: typed.ActorSystem[Nothing] = ctx.system
  implicit lazy val executionContext                   = system.executionContext
  implicit lazy val _timeout: Timeout                  = 2.seconds
  
  // set updater to self
  streamOperatorMonitor.processedMessages
    .setUpdater(result => Await.result(self.ask[Unit](ref => CollectProcessed(result, ref)), 1.second)) // really not cool way to do it

  streamOperatorMonitor.operators
    .setUpdater(result => Await.result(self.ask[Unit](ref => CollectOperators(result, ref)), 1.second)) // really not cool way to do it

  private[this] val connectionGraph: mutable.Map[UniqueStageInfo, Set[ConnectionStats]] =
    mutable.Map.empty
  private[this] val globalStreamMonitor = streamMonitor.bind(GlobalLabels(node))

  private[this] var stageSnapshot: Option[Seq[SnapshotEntry]] = None

  system.receptionist ! Register(streamServiceKey, messageAdapter(StatsReceived.apply))

  override def onMessage(msg: Command): Behavior[Command] = msg match {
    case StartStreamCollection(refs) if refs.nonEmpty =>
      log.info("Start stream stats collection")
      scheduler.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

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
      case SnapshotEntry(UniqueStageInfo(_, stageInfo), value, direction, connectedWith) =>
        log.trace("Reporting data of stage {}", stageInfo)
        val labels =
          Labels(stageInfo.stageName, node, Some(stageInfo.streamName.name), Some(connectedWith -> direction))
        result.observe(value, labels)
    })
    ref ! ()
  }

  def observeOperators(result: LazyResult[Long, Labels], ref: typed.ActorRef[Unit]): Unit = {
    log.debug("Reporting running operators")
    stageSnapshot.foreach(_.groupBy(_.stage.info.streamName).foreach {
      case (streamName, snapshots) =>
        snapshots.groupBy(_.stage.info.stageName).foreach {
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

  def collecting(refs: Set[ActorRef]): Behavior[Command] =
    Behaviors
      .receiveMessage[Command] {
        case StatsReceived(ActorInterpreterStats(ref, stageInfo, connections, _)) =>
          val refsLeft = refs - ref
          log.trace("Received stats from {}", ref)

          val stages = stageInfo.map { info =>
            val in = connections.filter(_.inName == info.stageName)
            (UniqueStageInfo(ref, info), in)
          }.toMap

          unwatch(ref)

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
}
