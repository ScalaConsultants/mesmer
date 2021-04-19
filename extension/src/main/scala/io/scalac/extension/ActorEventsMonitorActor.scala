package io.scalac.extension

import java.util.concurrent.atomic.AtomicReference

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.TimerScheduler
import akka.util.Timeout
import akka.{ actor => classic }
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import io.scalac.core.model.Node
import io.scalac.core.model.Tag
import io.scalac.core.util.ActorCellOps
import io.scalac.core.util.ActorRefOps
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.actor.ActorCellDecorator
import io.scalac.extension.actor.ActorMetricStorage
import io.scalac.extension.actor.ActorMetrics
import io.scalac.extension.metric.ActorMetricsMonitor
import io.scalac.extension.metric.ActorMetricsMonitor.Labels
import io.scalac.extension.metric.MetricObserver.Result
import io.scalac.extension.service.ActorTreeService
import io.scalac.extension.service.ActorTreeService.Command.GetActors
import io.scalac.extension.service.actorTreeServiceKey
import io.scalac.extension.util.GenericBehaviors

object ActorEventsMonitorActor {

  sealed trait Command
  private[ActorEventsMonitorActor] final case object StartActorsMeasurement                       extends Command
  private[ActorEventsMonitorActor] final case class MeasureActorTree(refs: Seq[classic.ActorRef]) extends Command
  private[ActorEventsMonitorActor] final case class ServiceListing(listing: Listing)              extends Command

  def apply(
    actorMonitor: ActorMetricsMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storageFactory: () => ActorMetricStorage,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      GenericBehaviors
        .waitForService(actorTreeServiceKey) { ref =>
          Behaviors.withTimers[Command] { scheduler =>
            new ActorEventsMonitorActor(
              ctx,
              actorMonitor,
              node,
              pingOffset,
              storageFactory,
              scheduler,
              actorMetricsReader
            ).start(ref)
          }
        }
    }

  trait ActorMetricsReader {
    def read(actor: classic.ActorRef): Option[ActorMetrics]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {

    private val logger = LoggerFactory.getLogger(getClass)

    def read(actor: classic.ActorRef): Option[ActorMetrics] =
      for {
        cell    <- ActorRefOps.Local.cell(actor)
        metrics <- ActorCellDecorator.get(cell)
      } yield ActorMetrics(
        mailboxSize = safeRead(ActorCellOps.numberOfMessages(cell)),
        mailboxTime = metrics.mailboxTimeAgg.metrics,
        processingTime = metrics.processingTimeAgg.metrics,
        receivedMessages = Some(metrics.receivedMessages.take()),
        unhandledMessages = Some(metrics.unhandledMessages.take()),
        failedMessages = Some(metrics.failedMessages.take()),
        sentMessages = Some(metrics.sentMessages.take()),
        stashSize = metrics.stashSize.get()
      )

    private def safeRead[T](value: => T): Option[T] =
      try Some(value)
      catch {
        case ex: Throwable =>
          logger.warn("Fail to read metric value", ex)
          None
      }

  }

}

private[extension] class ActorEventsMonitorActor private[extension] (
  context: ActorContext[Command],
  monitor: ActorMetricsMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  storageFactory: () => ActorMetricStorage,
  scheduler: TimerScheduler[Command],
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
) {

  import context._

  private[this] val boundMonitor = monitor.bind()

  private[this] val treeSnapshot = new AtomicReference[Option[Seq[(Labels, ActorMetrics)]]](None)

  private def updateMetric(extractor: ActorMetrics => Option[Long])(result: Result[Long, Labels]): Unit = {
    val state = treeSnapshot.get()
    state
      .foreach(_.foreach { case (labels, metrics) =>
        extractor(metrics).foreach(value => result.observe(value, labels))
      })
  }

  // this is not idempotent!
  private def registerUpdaters(): Unit = {
    boundMonitor.mailboxSize.setUpdater(updateMetric(_.mailboxSize))
    boundMonitor.failedMessages.setUpdater(updateMetric(_.failedMessages))
    boundMonitor.processedMessages.setUpdater(updateMetric(_.processedMessages))
    boundMonitor.receivedMessages.setUpdater(updateMetric(_.receivedMessages))
    boundMonitor.mailboxTimeAvg.setUpdater(updateMetric(_.mailboxTime.map(_.avg)))
    boundMonitor.mailboxTimeMax.setUpdater(updateMetric(_.mailboxTime.map(_.max)))
    boundMonitor.mailboxTimeMin.setUpdater(updateMetric(_.mailboxTime.map(_.min)))
    boundMonitor.mailboxTimeSum.setUpdater(updateMetric(_.mailboxTime.map(_.sum)))
    boundMonitor.processingTimeAvg.setUpdater(updateMetric(_.processingTime.map(_.avg)))
    boundMonitor.processingTimeMin.setUpdater(updateMetric(_.processingTime.map(_.min)))
    boundMonitor.processingTimeMax.setUpdater(updateMetric(_.processingTime.map(_.max)))
    boundMonitor.processingTimeSum.setUpdater(updateMetric(_.processingTime.map(_.sum)))
    boundMonitor.sentMessages.setUpdater(updateMetric(_.sentMessages))
    boundMonitor.stashSize.setUpdater(updateMetric(_.stashSize))
  }

  // this is not idempotent
  def start(treeService: ActorRef[ActorTreeService.Command]): Behavior[Command] = {

    setTimeout()
    registerUpdaters()
    loop(treeService)
  }

  private def loop(actorService: ActorRef[ActorTreeService.Command]): Behavior[Command] = {
    implicit val timeout: Timeout = 2.seconds

    Behaviors.receiveMessagePartial[Command] {
      case StartActorsMeasurement =>
        context
          .ask[ActorTreeService.Command, Seq[classic.ActorRef]](actorService, adapter => GetActors(Tag.all, adapter)) {
            case Success(value) => MeasureActorTree(value)
            case Failure(_)     => StartActorsMeasurement // keep asking
          }
        Behaviors.same
      case MeasureActorTree(refs) =>
        update(refs)
        setTimeout() // loop
        Behaviors.same
    }
  }.receiveSignal { case (_, PreRestart | PostStop) =>
    boundMonitor.unbind()
    Behaviors.same
  }

  private def setTimeout(): Unit = scheduler.startSingleTimer(StartActorsMeasurement, pingOffset)

  private def update(refs: Seq[classic.ActorRef]): Unit = {
    val storage = refs.foldLeft(storageFactory()) { case (acc, ref) =>
      actorMetricsReader.read(ref).fold(acc)(acc.save(ref, _))
    }

    captureState(storage)
  }

  private def captureState(storage: ActorMetricStorage): Unit = {
    log.debug("Capturing current actor tree state")
    treeSnapshot.set(Some(storage.snapshot.map { case (key, metrics) =>
      (Labels(key, node), metrics)
    }))
  }

}
