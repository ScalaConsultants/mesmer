package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, TimerScheduler }
import akka.actor.{ ActorRef, ActorRefProvider, ActorSystem }
import io.scalac.core.Tag
import io.scalac.core.util.Timestamp
import io.scalac.extension.ActorEventsMonitorActor.Command.{ AddTag, UpdateActorMetrics }
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.actor.{ ActorMetricStorage, ActorMetrics }
import io.scalac.extension.event.TagEvent
import io.scalac.extension.metric.ActorMetricMonitor
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.model.{ ActorKey, Node }

import scala.collection.{ immutable, mutable }
import scala.concurrent.duration._

class ActorEventsMonitorActor(
  monitor: ActorMetricMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  ctx: ActorContext[Command],
  scheduler: TimerScheduler[Command],
  actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
) {
  import ActorEventsMonitorActor._
  import ctx.log

  private[this] val actorTags: mutable.Map[ActorKey, mutable.Set[Tag]] = mutable.Map.empty

  def start(storage: ActorMetricStorage): Behavior[Command] =
    updateActorMetrics(storage)

  private def behavior(storage: ActorMetricStorage): Behavior[Command] = {
    registerUpdaters(storage)
    Behaviors.receiveMessage {
      case UpdateActorMetrics =>
        cleanTags(storage)
        updateActorMetrics(storage)
      case AddTag(ref, tag) =>
        ctx.log.trace(s"Add tags {} for actor {}", tag, ref)
        actorTags
          .getOrElseUpdate(storage.actorToKey(ref), mutable.Set.empty)
          .add(tag)
        Behaviors.same
    }
  }

  /**
   * Clean tags that wasn't found in last actor tree traversal
   * @param storage
   */
  private def cleanTags(storage: ActorMetricStorage): Unit = actorTags.keys.foreach { key =>
    actorTags.updateWith(key) {
      case s @ Some(_) if storage.has(key) => s
      case _                               => None
    }
  }

  private def registerUpdaters(storage: ActorMetricStorage): Unit =
    storage.foreach {
      case (key, metrics) =>
        metrics.mailboxSize.foreach { mailboxSize =>
          log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, mailboxSize)

          val tags = actorTags.get(key).fold[Set[Tag]](Set.empty)(_.toSet)
          monitor
            .bind(Labels(key, node, tags))
            .mailboxSize
            .setUpdater(_.observe(mailboxSize))
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
  object Command {
    final case object UpdateActorMetrics                                                   extends Command
    final private[ActorEventsMonitorActor] case class AddTag(actorRef: ActorRef, tag: Tag) extends Command
  }

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
        ctx.system.receptionist ! Register(tagServiceKey, ctx.messageAdapter[TagEvent] {
          case TagEvent(ref, tag) => AddTag(ref, tag)
        })
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
