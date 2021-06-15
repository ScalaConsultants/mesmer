package io.scalac.mesmer.extension

import java.util.concurrent.atomic.AtomicReference

import akka.Done
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

import io.scalac.mesmer.core.akka.actorPathPartialOrdering
import io.scalac.mesmer.core.model.ActorKey
import io.scalac.mesmer.core.model.ActorRefDetails
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.core.util.ActorCellOps
import io.scalac.mesmer.core.util.ActorPathOps
import io.scalac.mesmer.core.util.ActorRefOps
import io.scalac.mesmer.extension.ActorEventsMonitorActor._
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import io.scalac.mesmer.extension.actor.ActorMetrics
import io.scalac.mesmer.extension.actor.MetricStorageFactory
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor.Labels
import io.scalac.mesmer.extension.metric.MetricObserver.Result
import io.scalac.mesmer.extension.service.ActorTreeService
import io.scalac.mesmer.extension.service.ActorTreeService.Command.GetActorTree
import io.scalac.mesmer.extension.service.ActorTreeService.Command.TagSubscribe
import io.scalac.mesmer.extension.service.actorTreeServiceKey
import io.scalac.mesmer.extension.util.GenericBehaviors
import io.scalac.mesmer.extension.util.Tree
import io.scalac.mesmer.extension.util.Tree.Tree
import io.scalac.mesmer.extension.util.Tree.TreeOrdering._
import io.scalac.mesmer.extension.util.Tree._
import io.scalac.mesmer.extension.util.TreeF

object ActorEventsMonitorActor {

  sealed trait Command
  private[extension] final case class StartActorsMeasurement(replyTo: Option[ActorRef[Done]]) extends Command
  private[ActorEventsMonitorActor] final case class MeasureActorTree(
    refs: Tree[ActorRefDetails],
    replyTo: Option[ActorRef[Done]]
  )                                                                                           extends Command
  private[ActorEventsMonitorActor] final case class ActorTerminateEvent(ref: ActorRefDetails) extends Command
  private[ActorEventsMonitorActor] final case class ServiceListing(listing: Listing)          extends Command

  private val noAckStartMeasurement: StartActorsMeasurement = StartActorsMeasurement(None)

  def apply(
    actorMonitor: ActorMetricsMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storageFactory: MetricStorageFactory[ActorKey],
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  )(implicit askTimeout: Timeout): Behavior[Command] =
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
            ).start(ref, loop = true)
          }
        }
    }

  /**
   * This trait is not side-effect free - aggregation of metrics depend
   * on this to report metrics that changed only from last read - this is required
   * to account for disappearing actors
   */
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
        stashSize = metrics.stashSize.take(),
        droppedMessages = metrics.droppedMessages.map(_.take())
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
  storageFactory: MetricStorageFactory[ActorKey],
  scheduler: TimerScheduler[Command],
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
)(implicit val askTimeout: Timeout) {

  import context._

  private[this] var terminatedActorsMetrics =
    Tree.builder[
      ActorKey,
      (ActorKey, ActorMetrics)
    ] // we aggregate only ActorMetrics to not prevent actor cell to be GC'd

  private[this] val boundMonitor = monitor.bind()

  private[this] val treeSnapshot = new AtomicReference[Option[Vector[(Labels, ActorMetrics)]]](None)

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
    boundMonitor.droppedMessages.setUpdater(updateMetric(_.droppedMessages))
  }

  private def subscribeToActorTermination(treeService: ActorRef[ActorTreeService.Command]): Unit =
    treeService ! TagSubscribe(Tag.terminated, context.messageAdapter[ActorRefDetails](ActorTerminateEvent.apply))

  /**
   * Creates behavior that ask treeService for current actor tree state and then aggregate metrics measurement for later export.
   * It has to be created once as it registerUpdaters - doint this twice will create resource leak
   *
   * @param treeService service to obtain actor refs from
   * @param loop parameter to control if actor should periodically measure actors
   * @return
   */
  def start(treeService: ActorRef[ActorTreeService.Command], loop: Boolean): Behavior[Command] = {

    subscribeToActorTermination(treeService)
    if (loop) setTimeout()
    registerUpdaters()
    receive(treeService, loop)
  }

  private def receive(actorService: ActorRef[ActorTreeService.Command], loop: Boolean): Behavior[Command] =
    Behaviors
      .receiveMessagePartial[Command] {
        case start @ StartActorsMeasurement(replyTo) =>
          context
            .ask[ActorTreeService.Command, Tree[ActorRefDetails]](actorService, adapter => GetActorTree(adapter)) {
              case Success(value) => MeasureActorTree(value, replyTo)
              case Failure(_)     => start // keep asking
            }

          receive(actorService, loop)
        case MeasureActorTree(refs, replyTo) =>
          update(refs)
          if (loop) setTimeout() // loop
          replyTo.foreach(_.tell(Done))
          receive(actorService, loop)
        case ActorTerminateEvent(details) =>
          val path = ActorPathOps.getPathString(details.ref)

          actorMetricsReader.read(details.ref).foreach { metrics =>
            terminatedActorsMetrics.insert(path, path -> metrics)
          }

          Behaviors.same
      }
      .receiveSignal { case (_, PreRestart | PostStop) =>
        boundMonitor.unbind()
        Behaviors.same
      }

  private def setTimeout(): Unit = scheduler.startSingleTimer(noAckStartMeasurement, pingOffset)

  private def update(refs: Tree[ActorRefDetails]): Unit = {

    val storage = refs.unfix.foldRight[storageFactory.Storage] { case TreeF(details, childrenMetrics) =>
      import details._

      // is fold better?
      val storage =
        if (childrenMetrics.isEmpty) storageFactory.createStorage
        else childrenMetrics.reduce(storageFactory.mergeStorage)

      actorMetricsReader.read(ref).fold(storage) { currentMetrics =>
        import configuration.reporting._
        val actorKey = ActorPathOps.getPathString(ref)

        storage.save(actorKey, currentMetrics, visible)
        if (aggregate) {
          val (metrics, builder) = terminatedActorsMetrics.removeAfter(actorKey) // get all terminated information
          terminatedActorsMetrics = builder //
          metrics.foreach { case (key, metric) =>
            storage.save(key, metric, persistent = false)
          }
          storage.compute(actorKey)
        } else storage
      }
    }

    captureState(storage)
  }

  private def captureState(storage: storageFactory.Storage): Unit = {
    log.debug("Capturing current actor tree state")

    val currentSnapshot = treeSnapshot.get().getOrElse(Vector.empty)
    val metrics = storage.iterable.map { case (key, metrics) =>
      currentSnapshot.find { case (labels, _) =>
        labels.actorPath == key
      }.fold((Labels(key, node), metrics)) { case (labels, existingMetrics) =>
        (labels, existingMetrics.combine(metrics))
      }
    }.toVector

    treeSnapshot.set(Some(metrics))
    log.trace("Current actor metrics state {}", metrics)
  }

}
