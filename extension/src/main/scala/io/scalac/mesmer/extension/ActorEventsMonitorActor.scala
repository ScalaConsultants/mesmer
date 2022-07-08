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

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.akka.actorPathPartialOrdering
import io.scalac.mesmer.core.model.ActorKey
import io.scalac.mesmer.core.model.ActorRefDetails
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.core.util.ActorCellOps
import io.scalac.mesmer.core.util.ActorPathOps
import io.scalac.mesmer.core.util.ActorRefOps
import io.scalac.mesmer.extension.ActorEventsMonitorActor._
import io.scalac.mesmer.extension.actor.ActorMetrics
import io.scalac.mesmer.extension.actor.MetricStorageFactory
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor
import io.scalac.mesmer.extension.metric.ActorMetricsMonitor.Attributes
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
  ) extends Command
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
   * This trait is not side-effect free - aggregation of metrics depend on this to report metrics that changed only from
   * last read - this is required to account for disappearing actors
   */
  trait ActorMetricsReader {
    def read(actor: classic.ActorRef): Option[ActorMetrics]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {

    private val logger = LoggerFactory.getLogger(getClass)

    def read(actor: classic.ActorRef): Option[ActorMetrics] =
      for {
        cell    <- ActorRefOps.Local.cell(actor)
        metrics <- ActorCellDecorator.getMetrics(cell)
      } yield ActorMetrics(
        mailboxSize = safeRead(ActorCellOps.numberOfMessages(cell)),
        mailboxTime = metrics.mailboxTimeAgg.toOption.flatMap(_.metrics),
        processingTime = metrics.processingTimeAgg.toOption.flatMap(_.metrics),
        receivedMessages = metrics.receivedMessages.toOption.map(_.take()),
        unhandledMessages = metrics.unhandledMessages.toOption.map(_.take()),
        failedMessages = metrics.failedMessages.toOption.map(_.take()),
        sentMessages = metrics.sentMessages.toOption.map(_.take()),
        stashSize = metrics.stashedMessages.toOption.map(_.take()),
        droppedMessages = metrics.droppedMessages.toOption.map(_.take())
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
    ] // we aggregate only ActorMetrics to not prevent actor cell to be GCed

  private[this] val boundMonitor = monitor.bind()

  private[this] val treeSnapshot = new AtomicReference[Option[Vector[(Attributes, ActorMetrics)]]](None)

  private def updateMetric(extractor: ActorMetrics => Option[Long])(result: Result[Long, Attributes]): Unit = {
    val state: Option[Vector[(Attributes, ActorMetrics)]] = treeSnapshot.get()
    state
      .foreach(_.foreach { case (attributes, metrics) =>
        extractor(metrics).foreach(value => result.observe(value, attributes))
      })
  }

  // this is not idempotent!
  private def registerUpdaters(): Unit = {

    import boundMonitor._

    mailboxSize.setUpdater(updateMetric(_.mailboxSize))
    failedMessages.setUpdater(updateMetric(_.failedMessages))
    processedMessages.setUpdater(updateMetric(_.processedMessages))
    receivedMessages.setUpdater(updateMetric(_.receivedMessages))
    mailboxTimeCount.setUpdater(updateMetric(_.mailboxTime.map(_.count)))
    mailboxTimeMax.setUpdater(updateMetric(_.mailboxTime.map(_.max)))
    mailboxTimeMin.setUpdater(updateMetric(_.mailboxTime.map(_.min)))
    mailboxTimeSum.setUpdater(updateMetric(_.mailboxTime.map(_.sum)))
    processingTimeCount.setUpdater(updateMetric(_.processingTime.map(_.count)))
    processingTimeMin.setUpdater(updateMetric(_.processingTime.map(_.min)))
    processingTimeMax.setUpdater(updateMetric(_.processingTime.map(_.max)))
    processingTimeSum.setUpdater(updateMetric(_.processingTime.map(_.sum)))
    sentMessages.setUpdater(updateMetric(_.sentMessages))
    stashedMessages.setUpdater(updateMetric(_.stashSize))
    droppedMessages.setUpdater(updateMetric(_.droppedMessages))

  }

  private def subscribeToActorTermination(treeService: ActorRef[ActorTreeService.Command]): Unit =
    treeService ! TagSubscribe(Tag.terminated, context.messageAdapter[ActorRefDetails](ActorTerminateEvent.apply))

  /**
   * Creates behavior that ask treeService for current actor tree state and then aggregate metrics measurement for later
   * export. It has to be created once as it registerUpdaters - doint this twice will create resource leak
   *
   * @param treeService
   *   service to obtain actor refs from
   * @param loop
   *   parameter to control if actor should periodically measure actors
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

  // TODO this needs proper tests
  /*
    Here we traverse snapshot of actor try bottom up and calculate metrics to export.
    Steps:
     - reduce metrics known from children or empty storage if none exists
     - read metrics of currently processed actor
     - if actor is set to aggregate data (compute metrics based on current actor and it's children)
      - extract metrics from all known terminated actors that where children of the current actor
        and remove those from terminatedActors tree. This is mostly important for shortly leaved actors otherwise it's metrics will be lost
      - compute metrics based for the current actor
    - otherwise pass current storage downstream

    Rules for calualting metris
     - disabled - metric will not be exported, but will be used for calculating aggregation of a parent
     - instance - metric will be exported individually for the actor and will be used for aggregation of a parent
     - group - metric will use all children metrics and itself and report it as it's own. It can be used also for calculating aggregation of a parent

   */
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
          terminatedActorsMetrics = builder
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
      currentSnapshot
        .find(_._1.actorPath == key) // finds if metric already exists
        .fold((Attributes(key, node), metrics)) { case (attributes, existingMetrics) =>
          (attributes, existingMetrics.addTo(metrics))
        }
    }.toVector

    treeSnapshot.set(Some(metrics))
    log.trace("Current actor metrics state {}", metrics)
  }

}
