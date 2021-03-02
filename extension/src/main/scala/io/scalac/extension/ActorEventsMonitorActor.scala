package io.scalac.extension

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.{ actor => classic }

import org.slf4j.LoggerFactory

import io.scalac.core.model.Tag
import io.scalac.core.util.Timestamp
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.actor.{ ActorMetricStorage, ActorMetrics, MailboxTime, MailboxTimeHolder }
import io.scalac.extension.event.{ ActorEvent, TagEvent }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.{ ActorEvent, TagEvent }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.{ ActorMetricMonitor, Unbind }
import io.scalac.extension.model.{ ActorKey, Node }
import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TimeSeries.LongTimeSeries
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
        Behaviors.receiveMessage[SyncCommand] {
          case ActorEventWrapper(actorEvent) => f(actorEvent)
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

    def start(): Behavior[SyncCommand] = ActorEventWrapper.receiveMessage {
      case StashMeasurement(size, path) =>
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
        ctx.system.receptionist ! Register(tagServiceKey, ctx.messageAdapter[TagEvent] {
          case TagEvent(ref, tag) => AddTag(ref, tag)
        })

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

    override def onSignal: PartialFunction[Signal, Behavior[AsyncCommand]] = {
      case PostStop | PreRestart =>
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
          .map(rawToActorMetrics)
          .foreach(metrics => storage = storage.save(actorRef, metrics))
        unbinds.remove(storage.actorToKey(actorRef))
      }

      def rawToActorMetrics(raw: RawActorMetrics): ActorMetrics =
        ActorMetrics(
          raw.mailboxSize,
          raw.mailboxTimes.filter(_.nonEmpty).map(ts => LongValueAggMetric.fromTimeSeries(new LongTimeSeries(ts))),
          Timestamp.create()
        )

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
      storage.foreach {
        case (key, metrics) =>
          lazy val bind = {
            val tags = actorTags.get(key).fold[Set[Tag]](Set.empty)(_.toSet)
            val bind = monitor.bind(Labels(key, node, tags))
            unbinds.put(key, bind)
            bind
          }

          metrics.mailboxSize.foreach { mailboxSize =>
            log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, mailboxSize)
            bind.mailboxSize.setUpdater(_.observe(mailboxSize))
          }

          metrics.mailboxTime.foreach { mailboxTime =>
            log.trace("Registering a new updaters for mailbox time for actor {} with value {}", key, mailboxTime)
            bind.mailboxTimeAvg.setUpdater(_.observe(mailboxTime.avg))
            bind.mailboxTimeMin.setUpdater(_.observe(mailboxTime.min))
            bind.mailboxTimeMax.setUpdater(_.observe(mailboxTime.max))
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
    def read(actor: classic.ActorRef): Option[RawActorMetrics]
  }

  case class RawActorMetrics(mailboxSize: Option[Int], mailboxTimes: Option[Array[MailboxTime]])

  object ReflectiveActorMetricsReader extends ActorMetricsReader {
    import ReflectiveActorMonitorsUtils._

    import java.lang.invoke.MethodType.methodType

    private val logger = LoggerFactory.getLogger(getClass)

    private val numberOfMessagesMethodHandler = {
      val mt = methodType(classOf[Int])
      lookup.findVirtual(cellClass, "numberOfMessages", mt)
    }

    def read(actor: classic.ActorRef): Option[RawActorMetrics] =
      Option
        .when(isLocalActorRefWithCell(actor)) {
          val cell = underlyingMethodHandler.invoke(actor)
          RawActorMetrics(
            safeRead(mailboxSize(cell)),
            safeRead(mailboxTimes(cell)).flatten
          )
        }

    private[extension] def readMailboxSize(actor: classic.ActorRef): Option[Int] =
      Option
        .when(isLocalActorRefWithCell(actor)) {
          val cell = underlyingMethodHandler.invoke(actor)
          safeRead(mailboxSize(cell))
        }
        .flatten

    private def safeRead[T](value: => T): Option[T] =
      try {
        Some(value)
      } catch {
        case ex: Throwable =>
          logger.warn("Fail to read metric value", ex)
          None
      }

    private def mailboxSize(cell: Object): Int =
      numberOfMessagesMethodHandler.invoke(cell).asInstanceOf[Int]

    private def mailboxTimes(cell: Object): Option[Array[MailboxTime]] =
      MailboxTimeHolder.takeTimes(cell)

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
