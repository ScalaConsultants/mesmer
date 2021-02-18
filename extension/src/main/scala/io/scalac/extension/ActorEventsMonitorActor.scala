package io.scalac.extension

import scala.collection.immutable
import scala.concurrent.duration._

import akka.actor.typed.{ Behavior, SupervisorStrategy }
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.{ ActorRef, ActorRefProvider, ActorSystem }

import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.{ ActorMetricStorage, ActorMetrics }
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.metric.ActorMetricMonitor
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.model.Node

object ActorEventsMonitorActor {

  sealed trait Command

  def apply(
    actorMonitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storage: ActorMetricStorage,
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
            AsyncMetricsActor(actorMonitor, node, pingOffset, storage, actorTreeTraverser, actorMetricsReader)
          )
          .onFailure(SupervisorStrategy.restart),
        "asyncMetricsActor"
      )
      ctx.watch(syncMetricsActor)
      ctx.watch(asyncMetricsActor)
      Behaviors.receiveMessage {
        case msg: SyncMetricsActor.SyncCommand =>
          syncMetricsActor ! msg
          Behaviors.same
        case msg: AsyncMetricsActor.AsyncCommand =>
          asyncMetricsActor ! msg
          Behaviors.same
      }
    }

  private object SyncMetricsActor {
    sealed trait SyncCommand extends Command
    object SyncCommand {
      def receiveMessage(f: ActorEvent => Behavior[SyncCommand]): Behavior[SyncCommand] =
        Behaviors.receiveMessage[SyncCommand] {
          case ActorEventWrapper(actorEvent) => f(actorEvent)
        }
    }
    final case class ActorEventWrapper(actorEvent: ActorEvent) extends SyncCommand

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

    def start(): Behavior[SyncCommand] = SyncCommand.receiveMessage {
      case StashMeasurement(size, path) =>
        log.trace(s"Recorded stash size for actor $path: $size")
        monitor.bind(Labels(path, node)).stashSize.setValue(size)
        Behaviors.same
    }
  }

  private object AsyncMetricsActor {
    sealed trait AsyncCommand            extends Command
    final case object UpdateActorMetrics extends AsyncCommand

    def apply(
      actorMonitor: ActorMetricMonitor,
      node: Option[Node],
      pingOffset: FiniteDuration,
      storage: ActorMetricStorage,
      actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
      actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
    ): Behavior[AsyncCommand] =
      Behaviors.setup[AsyncCommand] { ctx =>
        Behaviors.withTimers[AsyncCommand] { scheduler =>
          new AsyncMetricsActor(actorMonitor, node, pingOffset, ctx, scheduler, actorTreeRunner, actorMetricsReader)
            .start(storage)
        }
      }
  }
  private class AsyncMetricsActor private (
    monitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    ctx: ActorContext[AsyncMetricsActor.AsyncCommand],
    scheduler: TimerScheduler[AsyncMetricsActor.AsyncCommand],
    actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ) {
    import ctx.log
    import AsyncMetricsActor._

    def start(storage: ActorMetricStorage): Behavior[AsyncCommand] =
      updateActorMetrics(storage)

    private def updateActorMetrics(storage: ActorMetricStorage): Behavior[AsyncCommand] = {

      def traverseActorTree(actor: ActorRef, storage: ActorMetricStorage): ActorMetricStorage =
        actorTreeRunner
          .getChildren(actor)
          .foldLeft(
            storage.save(actor, collect(actor))
          ) { case (storage, children) => traverseActorTree(children, storage) }

      def collect(actorRef: ActorRef): ActorMetrics =
        ActorMetrics(
          mailboxSize = actorMetricsReader.mailboxSize(actorRef),
          timestamp = Timestamp.create()
        )

      val nextStorage = traverseActorTree(actorTreeRunner.getRootGuardian(ctx.system.classicSystem), storage)

      scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

      behavior(nextStorage)
    }

    private def behavior(storage: ActorMetricStorage): Behavior[AsyncCommand] = {
      registerUpdaters(storage)
      Behaviors.receiveMessage {
        case UpdateActorMetrics => updateActorMetrics(storage)
      }
    }

    private def registerUpdaters(storage: ActorMetricStorage): Unit =
      storage.foreach {
        case (key, metrics) =>
          metrics.mailboxSize.foreach { mailboxSize =>
            log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, mailboxSize)
            monitor.bind(Labels(key, node)).mailboxSize.setUpdater(_.observe(mailboxSize))
          }
      }
  }

  trait ActorTreeTraverser {
    def getChildren(actor: ActorRef): immutable.Iterable[ActorRef]
    def getRootGuardian(system: ActorSystem): ActorRef
  }

  object ReflectiveActorTreeTraverser extends ActorTreeTraverser {
    import ReflectiveActorMonitorsUtils._

    import java.lang.invoke.MethodType.methodType

    private val actorRefProviderClass = classOf[ActorRefProvider]

    private val providerMethodHandler = {
      val mt = methodType(actorRefProviderClass)
      lookup.findVirtual(classOf[ActorSystem], "provider", mt)
    }

    private val rootGuardianMethodHandler = {
      val mt = methodType(Class.forName("akka.actor.InternalActorRef"))
      lookup.findVirtual(actorRefProviderClass, "rootGuardian", mt)
    }

    private val childrenMethodHandler = {
      val mt = methodType(classOf[immutable.Iterable[ActorRef]])
      lookup.findVirtual(actorRefWithCellClass, "children", mt)
    }

    def getChildren(actor: ActorRef): immutable.Iterable[ActorRef] =
      if (isLocalActorRefWithCell(actor)) {
        childrenMethodHandler.invoke(actor).asInstanceOf[immutable.Iterable[ActorRef]]
      } else {
        immutable.Iterable.empty
      }

    def getRootGuardian(system: ActorSystem): ActorRef = {
      val provider = providerMethodHandler.invoke(system)
      rootGuardianMethodHandler.invoke(provider).asInstanceOf[ActorRef]
    }
  }

  trait ActorMetricsReader {
    def mailboxSize(actor: ActorRef): Option[Int]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {
    import ReflectiveActorMonitorsUtils._

    import java.lang.invoke.MethodType.methodType

    private val numberOfMessagesMethodHandler = {
      val mt = methodType(classOf[Int])
      lookup.findVirtual(cellClass, "numberOfMessages", mt)
    }

    def mailboxSize(actor: ActorRef): Option[Int] =
      if (isLocalActorRefWithCell(actor)) {
        val cell = underlyingMethodHandler.invoke(actor)
        Some(numberOfMessagesMethodHandler.invoke(cell).asInstanceOf[Int])
      } else None

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

    def isLocalActorRefWithCell(actorRef: ActorRef): Boolean =
      actorRefWithCellClass.isInstance(actorRef) &&
        isLocalMethodHandler.invoke(underlyingMethodHandler.invoke(actorRef)).asInstanceOf[Boolean]
  }

}
