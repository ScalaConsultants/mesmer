package io.scalac.extension

import scala.collection.immutable
import scala.concurrent.duration._

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.{ ActorRef, ActorRefProvider, ActorSystem }

import io.scalac.core.util.Timestamp
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.actor.{ ActorMetricStorage, ActorMetrics }
import io.scalac.extension.metric.{ ActorMetricMonitor, Unbind }
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.model.Node

class ActorEventsMonitorActor(
  monitor: ActorMetricMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  ctx: ActorContext[Command],
  scheduler: TimerScheduler[Command],
  actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
) {
  import ctx.log
  import ActorEventsMonitorActor._

  def start(storage: ActorMetricStorage): Behavior[Command] =
    updateActorMetrics(storage, Map.empty)

  private def updateActorMetrics(storage: ActorMetricStorage, unbinds: Map[String, Unbind]): Behavior[Command] = {

    def traverseActorTree(
      actor: ActorRef,
      storage: ActorMetricStorage,
      unbinds: Map[String, Unbind]
    ): (ActorMetricStorage, Map[String, Unbind]) =
      actorTreeRunner
        .getChildren(actor)
        .foldLeft(
          (storage.save(actor, collect(actor)), unbinds - storage.actorToKey(actor))
        ) { case ((accStorage, accUnbinds), children) => traverseActorTree(children, accStorage, accUnbinds) }

    def collect(actorRef: ActorRef): ActorMetrics =
      ActorMetrics(
        mailboxSize = actorMetricsReader.mailboxSize(actorRef),
        timestamp = Timestamp.create()
      )

    val (nextStorage, nextUnbinds) =
      traverseActorTree(actorTreeRunner.getRootGuardian(ctx.system.classicSystem), storage, unbinds)

    scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

    behavior(nextStorage, nextUnbinds)
  }

  private def behavior(storage: ActorMetricStorage, unbinds: Map[String, Unbind]): Behavior[Command] = {
    log.trace("Unbind these actors: {}", unbinds.keys.mkString("[", ",", "]"))
    unbinds.values.foreach(_.unbind())
    val nextStorage = unbinds.keys.foldLeft(storage)(_.remove(_))
    val nextUnbinds = registerUpdaters(storage)
    Behaviors.receiveMessage {
      case UpdateActorMetrics => updateActorMetrics(nextStorage, nextUnbinds)
    }
  }

  private def registerUpdaters(storage: ActorMetricStorage): Map[String, Unbind] =
    storage.map {
      case (key, metrics) =>
        var bind: Option[ActorMetricMonitor.BoundMonitor] = None
        metrics.mailboxSize.foreach { mailboxSize =>
          log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, mailboxSize)
          bind = Some(monitor.bind(Labels(key, node)))
          bind.get.mailboxSize.setUpdater(_.observe(mailboxSize))
        }
        bind.map((key, _))
    }.collect { case Some(bind) => bind }.toMap

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
        new ActorEventsMonitorActor(actorMonitor, node, pingOffset, ctx, scheduler, actorTreeRunner, actorMetricsReader)
          .start(storage)
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
