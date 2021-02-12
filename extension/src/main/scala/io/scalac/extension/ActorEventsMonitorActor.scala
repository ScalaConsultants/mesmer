package io.scalac.extension

import scala.collection.immutable
import scala.concurrent.duration._

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.{ ActorRef, ActorRefProvider, ActorSystem }

import io.scalac.core.util.Timestamp
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.actor.{ ActorMetricStorage, ActorMetrics }
import io.scalac.extension.metric.ActorMetricMonitor
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
    updateActorMetrics(storage)

  private def behavior(storage: ActorMetricStorage): Behavior[Command] = {
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

  private def updateActorMetrics(storage: ActorMetricStorage): Behavior[Command] = {

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

    traverseActorTree(actorTreeRunner.getRootGuardian(ctx.system.classicSystem), storage)

    scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

    behavior(storage)
  }
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

    private val getSystemProviderReflectively = {
      classOf[ActorSystem]
        .getMethod("provider")
    }

    private val getRootGuardianReflectively = {
      classOf[ActorRefProvider]
        .getMethod("rootGuardian")
    }

    private val getChildrenReflectively = {
      actorRefWithCellClass
        .getMethod("children")
    }

    def getChildren(actor: ActorRef): immutable.Iterable[ActorRef] =
      if (isLocalActorRefWithCell(actor)) {
        getChildrenReflectively.invoke(actor).asInstanceOf[immutable.Iterable[ActorRef]]
      } else {
        immutable.Iterable.empty
      }

    def getRootGuardian(system: ActorSystem): ActorRef = {
      val provider = getSystemProviderReflectively.invoke(system)
      getRootGuardianReflectively.invoke(provider).asInstanceOf[ActorRef]
    }
  }

  trait ActorMetricsReader {
    def mailboxSize(actor: ActorRef): Option[Int]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {
    import ReflectiveActorMonitorsUtils._

    private val getNumberOfMessagesReflectively = cellClass.getDeclaredMethod("numberOfMessages")

    def mailboxSize(actor: ActorRef): Option[Int] =
      if (isLocalActorRefWithCell(actor)) {
        val cell = underlyingReflectively.invoke(actor)
        Some(getNumberOfMessagesReflectively.invoke(cell).asInstanceOf[Int])
      } else None

  }

  private object ReflectiveActorMonitorsUtils {
    private[ActorEventsMonitorActor] val actorRefWithCellClass  = Class.forName("akka.actor.ActorRefWithCell")
    private[ActorEventsMonitorActor] val cellClass              = Class.forName("akka.actor.Cell")
    private[ActorEventsMonitorActor] val underlyingReflectively = actorRefWithCellClass.getDeclaredMethod("underlying")
    private val isLocalReflectively                             = cellClass.getDeclaredMethod("isLocal")
    def isLocalActorRefWithCell(actorRef: ActorRef): Boolean =
      actorRefWithCellClass.isInstance(actorRef) &&
        isLocalReflectively.invoke(underlyingReflectively.invoke(actorRef)).asInstanceOf[Boolean]
  }

}
