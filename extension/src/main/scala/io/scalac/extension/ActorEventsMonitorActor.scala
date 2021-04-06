package io.scalac.extension

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.{ actor => classic }
import io.scalac.core._
import io.scalac.core.actor.{ ActorCellDecorator, ActorMetricStorage, ActorMetrics }
import io.scalac.core.event.TagEvent
import io.scalac.core.model.{ ActorKey, Node, Tag }
import io.scalac.core.util.{ ActorCellOps, ActorRefOps }
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.metric.ActorMetricMonitor
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.MetricObserver.Result
import io.scalac.extension.service.{ ActorTreeTraverser, ReflectiveActorTreeTraverser }
import org.slf4j.LoggerFactory

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._

object ActorEventsMonitorActor {

  sealed trait Command
  private[ActorEventsMonitorActor] final case object UpdateActorMetrics                          extends Command
  private[ActorEventsMonitorActor] final case class AddTag(actorRef: classic.ActorRef, tag: Tag) extends Command

  def apply(
    actorMonitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storage: ActorMetricStorage,
    streamRef: ActorRef[AkkaStreamMonitoring.Command],
    actorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      ActorEventsMonitorActor(
        ctx,
        actorMonitor,
        node,
        pingOffset,
        storage,
        streamRef,
        actorTreeTraverser,
        actorMetricsReader
      )
    }

  private def apply(
    context: ActorContext[Command],
    actorMonitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storage: ActorMetricStorage,
    streamRef: ActorRef[AkkaStreamMonitoring.Command],
    actorTreeRunner: ActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader
  ): Behavior[Command] = {
    context.system.receptionist ! Register(
      tagServiceKey,
      context.messageAdapter[TagEvent] { case TagEvent(ref, tag) =>
        AddTag(ref, tag)
      }
    )

    Behaviors.withTimers[Command] { scheduler =>
      new ActorEventsMonitorActor(
        context,
        actorMonitor,
        node,
        pingOffset,
        storage,
        streamRef,
        scheduler,
        actorTreeRunner,
        actorMetricsReader
      )
    }
  }

  trait ActorMetricsReader {
    def read(actor: classic.ActorRef): Option[ActorMetrics]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {

    private val logger = (LoggerFactory.getLogger(getClass))

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

private class ActorEventsMonitorActor private (
  ctx: ActorContext[Command],
  monitor: ActorMetricMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  private var storage: ActorMetricStorage,
  streamRef: ActorRef[AkkaStreamMonitoring.Command],
  scheduler: TimerScheduler[Command],
  actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
) extends AbstractBehavior[Command](ctx) {

  import context._
  // Disclaimer:
  // Due to the compute intensiveness of traverse the actors tree,
  // we're using AbstractBehavior, mutable state and var in intention to boost our performance.

  private[this] val actorTags: mutable.Map[ActorKey, mutable.Set[Tag]] = mutable.Map.empty

  private[this] var refs: List[classic.ActorRef] = Nil

  private[this] val boundMonitor = monitor.bind()

  private[this] val treeSnapshot = new AtomicReference[Option[Seq[(Labels, ActorMetrics)]]](None)

  init()

  private def updateMetric(extractor: ActorMetrics => Option[Long])(result: Result[Long, Labels]): Unit = {
    val state = treeSnapshot.get()
    state
      .foreach(_.foreach { case (labels, metrics) =>
        extractor(metrics).foreach(value => result.observe(value, labels))
      })
  }

  // this is not idempotent
  private def init(): Unit = {
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
    //start collection loop
    setTimeout()
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = { case PostStop | PreRestart =>
    boundMonitor.unbind()
    this
  }

  def onMessage(msg: Command): Behavior[Command] = msg match {
    case UpdateActorMetrics =>
      update()
      cleanTags()
      cleanRefs()
      setTimeout() // loop
      this
    case AddTag(ref, tag) =>
      log.trace(s"Add tags {} for actor {}", tag, ref)
      refs ::= ref
      actorTags
        .getOrElseUpdate(storage.actorToKey(ref), mutable.Set.empty)
        .add(tag)
      this
  }

  private def setTimeout(): Unit = scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

  /**
   * Clean tags that wasn't found in last actor tree traversal
   */
  private def cleanTags(): Unit =
    actorTags.keys.foreach { key =>
      actorTags.updateWith(key) {
        case s @ Some(_) if storage.has(key) => s
        case _                               => None
      }
    }

  private def cleanRefs(): Unit =
    refs = refs.filter(ref => storage.has(storage.actorToKey(ref)))

  private def update(): Unit = {

    @tailrec
    def traverseActorTree(actors: List[classic.ActorRef]): Unit = actors match {
      case Nil =>
      case h :: t =>
        read(h)
        val nextActors = t.prependedAll(actorTreeRunner.getChildren(h))
        traverseActorTree(nextActors)
    }

    def read(actorRef: classic.ActorRef): Unit =
      actorMetricsReader
        .read(actorRef)
        .foreach(metrics => storage = storage.save(actorRef, metrics))

    traverseActorTree(actorTreeRunner.getRootGuardian(ctx.system.classicSystem) :: Nil)

    runSideEffects()
  }

  private def runSideEffects(): Unit = {
    startStreamCollection()
    captureState()
  }

  private def startStreamCollection(): Unit = streamRef ! StartStreamCollection(refs.toSet)

  private def captureState(): Unit = {
    log.debug("Capturing current actor tree state")
    treeSnapshot.set(Some(storage.snapshot.map { case (key, metrics) =>
      val tags = actorTags.get(key).fold[Set[Tag]](Set.empty)(_.toSet)
      (Labels(key, node, tags), metrics)
    }))
    storage = storage.clear()
  }

}
