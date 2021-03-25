package io.scalac.extension

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.{ actor => classic }
import io.scalac.core.model.{ ActorKey, Node, Tag }
import io.scalac.core.util.{ ActorCellOps, ActorRefOps }
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.actor.{ ActorCountsDecorators, ActorMetricStorage, ActorMetrics, ActorTimesDecorators }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.{ ActorEvent, TagEvent }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.{ ActorMetricMonitor, Unbind }
import org.slf4j.LoggerFactory

import java.lang.invoke.MethodHandles
import scala.annotation.tailrec
import scala.collection.{ immutable, mutable }
import scala.concurrent.duration._

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
    import SyncMetricsActor._
    import ctx.{ log, messageAdapter, system }

    Receptionist(system).ref ! Register(
      actorServiceKey,
      messageAdapter(ActorEventWrapper.apply)
    )

    def start(): Behavior[SyncCommand] = ActorEventWrapper.receiveMessage { case StashMeasurement(size, path) =>
      log.trace(s"Recorded stash size for actor $path: $size")
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
    // Disclaimer:
    // Due to the compute intensiveness of traverse the actors tree,
    // we're using AbstractBehavior, mutable state and var in intention to boost our performance.

    import AsyncMetricsActor._
    import ctx.log

    private[this] val actorTags: mutable.Map[ActorKey, mutable.Set[Tag]] = mutable.Map.empty

    private[this] var refs: List[classic.ActorRef] = Nil

    private[this] val unbinds = mutable.Map.empty[ActorKey, Unbind]

    // start
    setTimeout()

    override def onSignal: PartialFunction[Signal, Behavior[AsyncCommand]] = { case PostStop | PreRestart =>
      storage.clear()
      unbinds.clear()
      actorTags.clear()
      refs = Nil
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
        ctx.log.trace(s"Add tags {} for actor {}", tag, ref)
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

      def read(actorRef: classic.ActorRef): Unit = {
        actorMetricsReader
          .read(actorRef)
          .foreach(metrics => storage = storage.save(actorRef, metrics))
        unbinds.remove(storage.actorToKey(actorRef))
      }

      traverseActorTree(actorTreeRunner.getRootGuardian(ctx.system.classicSystem) :: Nil)

      runSideEffects()
    }

    private def runSideEffects(): Unit = {
      startStreamCollection()
      callUnbinds()
      resetStorage()
      registerUpdaters()
    }

    private def startStreamCollection(): Unit = streamRef ! StartStreamCollection(refs.toSet)

    private def callUnbinds(): Unit = unbinds.foreach(_._2.unbind())

    private def resetStorage(): Unit = storage = unbinds.keys.foldLeft(storage)(_.remove(_))

    private def registerUpdaters(): Unit =
      storage.foreach { case (key, metrics) =>
        lazy val bind = {
          val tags = actorTags.get(key).fold(Set.empty[Tag])(_.toSet)
          val bind = monitor.bind(Labels(key, node, tags))
          unbinds.put(key, bind)
          bind
        }

        metrics.mailboxSize.filter(0.<).foreach { ms =>
          log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, ms)
          bind.mailboxSize.setUpdater(_.observe(ms))
        }

        metrics.mailboxTime.foreach { mt =>
          log.trace("Registering a new updaters for mailbox time for actor {} with value {}", key, mt)
          bind.mailboxTimeAvg.setUpdater(_.observe(mt.avg))
          bind.mailboxTimeMin.setUpdater(_.observe(mt.min))
          bind.mailboxTimeMax.setUpdater(_.observe(mt.max))
          bind.mailboxTimeSum.setUpdater(_.observe(mt.sum))
        }

        metrics.receivedMessages.filter(0.<).foreach { rm =>
          log.trace("Registering a new updater for received messages for actor {} with value {}", key, rm)
          bind.receivedMessages.setUpdater(_.observe(rm))
        }

        metrics.processedMessages.filter(0.<).foreach { pm =>
          log.trace("Registering a new updater for processed messages for actor {} with value {}", key, pm)
          bind.processedMessages.setUpdater(_.observe(pm))
        }

        metrics.failedMessages.filter(0.<).foreach { fm =>
          log.trace("Registering a new updater for failed messages for actor {} with value {}", key, fm)
          bind.failedMessages.setUpdater(_.observe(fm))
        }

        metrics.processingTime.foreach { pt =>
          log.trace("Registering a new updaters for processing time for actor {} with value {}", key, pt)
          bind.processingTimeAvg.setUpdater(_.observe(pt.avg))
          bind.processingTimeMin.setUpdater(_.observe(pt.min))
          bind.processingTimeMax.setUpdater(_.observe(pt.max))
          bind.processingTimeSum.setUpdater(_.observe(pt.sum))
        }

        metrics.sentMessages.foreach { sm =>
          log.trace("Registering a new updaters for sent messages for actor {} with value {}", key, sm)
          bind.sentMessages.setUpdater(_.observe(sm))
        }

        metrics.stashSize.foreach { ss =>
          log.trace("Registering a new updaters for sent messages for actor {} with value {}", key, ss)
          bind.stashSize.setUpdater(_.observe(ss))
        }

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

    private val logger = LoggerFactory.getLogger(getClass)

    def read(actor: classic.ActorRef): Option[ActorMetrics] =
      ActorRefOps.Local
        .cell(actor)
        .map { cell =>
          ActorMetrics(
            mailboxSize = safeRead(ActorCellOps.numberOfMessages(cell)),
            mailboxTime = ActorTimesDecorators.MailboxTime.getMetrics(cell),
            processingTime = ActorTimesDecorators.ProcessingTime.getMetrics(cell),
            receivedMessages = ActorCountsDecorators.Received.take(cell),
            unhandledMessages = ActorCountsDecorators.Unhandled.take(cell),
            failedMessages = ActorCountsDecorators.Failed.take(cell),
            sentMessages = ActorCountsDecorators.Sent.take(cell),
            stashSize = ActorCountsDecorators.Stash.take(cell)
          )
        }

    private def safeRead[T](value: => T): Option[T] =
      try Some(value)
      catch {
        case ex: Throwable =>
          logger.warn("Fail to read metric value", ex)
          None
      }

  }

}
