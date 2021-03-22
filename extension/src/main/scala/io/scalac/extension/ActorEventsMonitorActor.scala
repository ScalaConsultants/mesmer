package io.scalac.extension

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.{ actor => classic }
import io.scalac.core.model.{ Tag, _ }
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.actor.{ ActorCountsDecorators, ActorMetricStorage, ActorMetrics, ActorTimesDecorators }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.{ ActorEvent, TagEvent }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.{ ActorMetricMonitor, Unbind }
import io.scalac.core.model.{ ActorKey, Node }
import org.slf4j.LoggerFactory

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
      monitor.bind(Labels(path, node)).stashSize.setValue(size)
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
          val tags = actorTags.get(key).fold[Set[Tag]](Set.empty)(_.toSet)
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

      }

    private def setTimeout(): Unit = scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

  }

  trait ActorTreeTraverser {
    def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef]
    def getRootGuardian(system: classic.ActorSystem): classic.ActorRef
  }

  object ReflectiveActorTreeTraverser extends ActorTreeTraverser {
    import ReflectiveActorMonitorsUtils._

    import java.lang.invoke.MethodType.methodType

    private val actorRefProviderClass = classOf[classic.ActorRefProvider]

    private val providerMethodHandler = {
      val mt = methodType(actorRefProviderClass)
      lookup.findVirtual(classOf[classic.ActorSystem], "provider", mt)
    }

    private val rootGuardianMethodHandler = {
      val mt = methodType(Class.forName("akka.actor.InternalActorRef"))
      lookup.findVirtual(actorRefProviderClass, "rootGuardian", mt)
    }

    private val childrenMethodHandler = {
      val mt = methodType(classOf[immutable.Iterable[classic.ActorRef]])
      lookup.findVirtual(actorRefWithCellClass, "children", mt)
    }

    def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef] =
      if (isLocalActorRefWithCell(actor)) {
        childrenMethodHandler.invoke(actor).asInstanceOf[immutable.Iterable[classic.ActorRef]]
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
    import ReflectiveActorMonitorsUtils._

    import java.lang.invoke.MethodType.methodType

    private val logger = LoggerFactory.getLogger(getClass)

    private val numberOfMessagesMethodHandler = {
      val mt = methodType(classOf[Int])
      lookup.findVirtual(cellClass, "numberOfMessages", mt)
    }

    def read(actor: classic.ActorRef): Option[ActorMetrics] =
      Option
        .when(isLocalActorRefWithCell(actor)) {
          val cell = underlyingMethodHandler.invoke(actor)
          ActorMetrics(
            mailboxSize = safeRead(mailboxSize(cell)),
            mailboxTime = ActorTimesDecorators.MailboxTime.getMetrics(cell),
            processingTime = ActorTimesDecorators.ProcessingTime.getMetrics(cell),
            receivedMessages = ActorCountsDecorators.Received.take(cell),
            unhandledMessages = ActorCountsDecorators.Unhandled.take(cell),
            failedMessages = ActorCountsDecorators.Failed.take(cell)
          )
        }

    private def safeRead[T](value: => T): Option[T] =
      try Some(value)
      catch {
        case ex: Throwable =>
          logger.warn("Fail to read metric value", ex)
          None
      }

    private def mailboxSize(cell: Object): Int =
      numberOfMessagesMethodHandler.invoke(cell).asInstanceOf[Int]

  }

  private object ReflectiveActorMonitorsUtils {
    import java.lang.invoke.MethodHandles
    import java.lang.invoke.MethodType.methodType

    private[ActorEventsMonitorActor] val lookup = MethodHandles.lookup()

    private[ActorEventsMonitorActor] val actorRefWithCellClass = Class.forName("akka.actor.ActorRefWithCell")
    private[ActorEventsMonitorActor] val cellClass             = Class.forName("akka.actor.Cell")
    private[ActorEventsMonitorActor] val underlyingMethodHandler =
      lookup.findVirtual(actorRefWithCellClass, "underlying", methodType(cellClass))

    private val isLocalMethodHandler = lookup.findVirtual(cellClass, "isLocal", methodType(classOf[Boolean]))

    def isLocalActorRefWithCell(actorRef: classic.ActorRef): Boolean =
      actorRefWithCellClass.isInstance(actorRef) &&
        isLocalMethodHandler.invoke(underlyingMethodHandler.invoke(actorRef)).asInstanceOf[Boolean]
  }

}
