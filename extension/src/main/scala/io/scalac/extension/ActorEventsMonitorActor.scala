package io.scalac.extension

import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicReference

import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.TimerScheduler
import akka.{ actor => classic }

import org.slf4j.LoggerFactory

import io.scalac.core.model.ActorKey
import io.scalac.core.model.Node
import io.scalac.core.model.Tag
import io.scalac.core.util.ActorCellOps
import io.scalac.core.util.ActorRefOps
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.actor.ActorCellDecorator
import io.scalac.extension.actor.ActorMetricStorage
import io.scalac.extension.actor.ActorMetrics
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.TagEvent
import io.scalac.extension.metric.ActorMetricMonitor
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.MetricObserver.Result

object ActorEventsMonitorActor {

  sealed trait Command

  def apply(
    actorMonitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storage: ActorMetricStorage,
    streamRef: ActorRef[AkkaStreamMonitoring.Command],
    actorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      val syncMetricsActor = ctx.spawn(
        Behaviors
          .supervise(
            SyncMetricsActor(actorMonitor, node)
          )
          .onFailure(SupervisorStrategy.restart),
        "syncMetricsActor"
      )
      val asyncMetricsActor = ctx.spawn(
        Behaviors
          .supervise(
            AsyncMetricsActor(
              actorMonitor,
              node,
              pingOffset,
              storage,
              streamRef,
              actorTreeTraverser,
              actorMetricsReader
            )
          )
          .onFailure(SupervisorStrategy.restart),
        "asyncMetricsActor"
      )
      ctx.watch(syncMetricsActor)
      ctx.watch(asyncMetricsActor)
      Behaviors.ignore
    }

  private object SyncMetricsActor {
    private[ActorEventsMonitorActor] sealed trait SyncCommand          extends Command
    private final case class ActorEventWrapper(actorEvent: ActorEvent) extends SyncCommand

    //TODO do we still need this?
    private object ActorEventWrapper {
      def receiveMessage(f: ActorEvent => Behavior[SyncCommand]): Behavior[SyncCommand] =
        Behaviors.receiveMessage[SyncCommand] { case ActorEventWrapper(actorEvent) =>
          f(actorEvent)
        }
    }

    def apply(monitor: ActorMetricMonitor, node: Option[Node]): Behavior[SyncCommand] =
      Behaviors.setup[SyncCommand](ctx => new SyncMetricsActor(monitor, node, ctx).start())
  }

  private class SyncMetricsActor private (
    monitor: ActorMetricMonitor,
    node: Option[Node],
    ctx: ActorContext[SyncMetricsActor.SyncCommand]
  ) {

    private val boundMonitor = monitor.bind()

    import SyncMetricsActor._
    import ctx.{ log, messageAdapter, system }

    Receptionist(system).ref ! Register(
      actorServiceKey,
      messageAdapter(ActorEventWrapper.apply)
    )

    def start(): Behavior[SyncCommand] = ActorEventWrapper.receiveMessage { case StashMeasurement(size, path) =>
      log.trace(s"Recorded stash size for actor $path: $size")
      boundMonitor.stashSize(Labels(path, node)).setValue(size)
      Behaviors.same
    }
  }

  private object AsyncMetricsActor {

    private[AsyncMetricsActor] sealed trait AsyncCommand                  extends Command
    private final case object UpdateActorMetrics                          extends AsyncCommand
    private final case class AddTag(actorRef: classic.ActorRef, tag: Tag) extends AsyncCommand

    def apply(
      actorMonitor: ActorMetricMonitor,
      node: Option[Node],
      pingOffset: FiniteDuration,
      storage: ActorMetricStorage,
      streamRef: ActorRef[AkkaStreamMonitoring.Command],
      actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
      actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
    ): Behavior[AsyncCommand] =
      Behaviors.setup[AsyncCommand] { ctx =>
        ctx.system.receptionist ! Register(
          tagServiceKey,
          ctx.messageAdapter[TagEvent] { case TagEvent(ref, tag) =>
            AddTag(ref, tag)
          }
        )

        Behaviors.withTimers[AsyncCommand] { scheduler =>
          new AsyncMetricsActor(
            actorMonitor,
            node,
            pingOffset,
            storage,
            streamRef,
            ctx,
            scheduler,
            actorTreeRunner,
            actorMetricsReader
          )
        }
      }
  }

  private class AsyncMetricsActor private (
    monitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    private var storage: ActorMetricStorage,
    streamRef: ActorRef[AkkaStreamMonitoring.Command],
    ctx: ActorContext[AsyncMetricsActor.AsyncCommand],
    scheduler: TimerScheduler[AsyncMetricsActor.AsyncCommand],
    actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ) extends AbstractBehavior[AsyncMetricsActor.AsyncCommand](ctx) {

    import context._
    // Disclaimer:
    // Due to the compute intensiveness of traverse the actors tree,
    // we're using AbstractBehavior, mutable state and var in intention to boost our performance.

    import AsyncMetricsActor._

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
      //start collection loop
      setTimeout()
    }

    override def onSignal: PartialFunction[Signal, Behavior[AsyncCommand]] = { case PostStop | PreRestart =>
      boundMonitor.unbind()
      this
    }

    def onMessage(msg: AsyncCommand): Behavior[AsyncCommand] = msg match {
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

  trait ActorTreeTraverser {
    def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef]
    def getRootGuardian(system: classic.ActorSystem): classic.ActorRef
  }

  object ReflectiveActorTreeTraverser extends ActorTreeTraverser {

    import java.lang.invoke.MethodType.methodType

    private val actorRefProviderClass = classOf[classic.ActorRefProvider]

    private val (providerMethodHandler, rootGuardianMethodHandler) = {
      val lookup = MethodHandles.lookup()
      (
        lookup.findVirtual(classOf[classic.ActorSystem], "provider", methodType(actorRefProviderClass)),
        lookup.findVirtual(
          actorRefProviderClass,
          "rootGuardian",
          methodType(Class.forName("akka.actor.InternalActorRef"))
        )
      )
    }

    def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef] =
      if (ActorRefOps.isLocal(actor)) {
        ActorRefOps.children(actor)
      } else {
        immutable.Iterable.empty
      }

    def getRootGuardian(system: classic.ActorSystem): classic.ActorRef = {
      val provider = providerMethodHandler.invoke(system)
      rootGuardianMethodHandler.invoke(provider).asInstanceOf[classic.ActorRef]
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
        sentMessages = Some(metrics.sentMessages.take())
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
