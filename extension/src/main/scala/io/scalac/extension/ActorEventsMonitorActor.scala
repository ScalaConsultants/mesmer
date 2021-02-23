package io.scalac.extension

import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.typed.{ Behavior, PostStop, PreRestart, Signal }
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.actor.{ ActorRef, ActorRefProvider, ActorSystem }

import io.scalac.core.util.Timestamp
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.actor.{ ActorKey, ActorMetricStorage, ActorMetrics }
import io.scalac.extension.metric.{ ActorMetricMonitor, Unbind }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.model.Node

final class ActorEventsMonitorActor(
  monitor: ActorMetricMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  private var storage: ActorMetricStorage,
  ctx: ActorContext[Command],
  scheduler: TimerScheduler[Command],
  actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
) extends AbstractBehavior[Command](ctx) {
  import ctx.log
  import ActorEventsMonitorActor._

  // Disclaimer:
  // Due to the compute intensiveness of traverse the actors tree,
  // we're using AbstractBehavior, mutable state and var in intention to boost our performance.

  private val unbinds = mutable.Map.empty[ActorKey, Unbind]

  setTimeout() // first call

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PreRestart =>
      setTimeout()
      this
    case PostStop =>
      storage.clear()
      unbinds.clear()
      this
  }

  def onMessage(msg: Command): Behavior[Command] = msg match {
    case UpdateActorMetrics =>
      update()
      setTimeout()
      this
  }

  private def update(): Unit = {

    @tailrec
    def traverseActorTree(actors: List[ActorRef]): Unit = actors match {
      case Nil =>
      case h :: t =>
        storage = storage.save(h, collect(h))
        unbinds.remove(storage.actorToKey(h))
        val nextActors = t ++ actorTreeRunner.getChildren(h)
        traverseActorTree(nextActors)
    }

    def collect(actorRef: ActorRef): ActorMetrics =
      ActorMetrics(
        mailboxSize = actorMetricsReader.mailboxSize(actorRef),
        timestamp = Timestamp.create()
      )

    traverseActorTree(actorTreeRunner.getRootGuardian(ctx.system.classicSystem) :: Nil)

    runSideEffects()
  }

  private def runSideEffects(): Unit = {
    callUnbinds()
    resetStorage()
    registerUpdaters()
  }

  private def callUnbinds(): Unit = unbinds.foreach(_._2.unbind())

  private def resetStorage(): Unit = storage = unbinds.keys.foldLeft(storage)(_.remove(_))

  private def registerUpdaters(): Unit =
    storage.foreach {
      case (key, metrics) =>
        metrics.mailboxSize.foreach { mailboxSize =>
          log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, mailboxSize)
          val bind = monitor.bind(Labels(key, node))
          bind.mailboxSize.setUpdater(_.observe(mailboxSize))
          unbinds.put(key, bind)
        }
    }

  private def setTimeout(): Unit = scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

}

object ActorEventsMonitorActor {

  sealed trait Command
  final case object UpdateActorMetrics extends Command

  def apply(
    actorMonitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storage: ActorMetricStorage,
    actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { scheduler =>
        new ActorEventsMonitorActor(
          actorMonitor,
          node,
          pingOffset,
          storage,
          ctx,
          scheduler,
          actorTreeRunner,
          actorMetricsReader
        )
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
