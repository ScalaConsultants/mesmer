package io.scalac.extension

import scala.annotation.tailrec
import scala.collection.{ immutable, mutable }
import scala.concurrent.duration._

import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors, TimerScheduler }
import akka.actor.typed._
import akka.{ actor => classic }

import io.scalac.core.model.Tag
import io.scalac.core.util.Timestamp
import io.scalac.extension.ActorEventsMonitorActor.Command.{ AddTag, UpdateActorMetrics }
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.actor.{ ActorMetricStorage, ActorMetrics }
import io.scalac.extension.event.TagEvent
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.{ ActorMetricMonitor, StreamMetricsMonitor, Unbind }
import io.scalac.extension.model.{ ActorKey, Node }

final class ActorEventsMonitorActor(
  monitor: ActorMetricMonitor,
  node: Option[Node],
  pingOffset: FiniteDuration,
  private var storage: ActorMetricStorage,
  ctx: ActorContext[Command],
  scheduler: TimerScheduler[Command],
  streamRef: ActorRef[AkkaStreamMonitoring.Command],
  actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
  actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
) extends AbstractBehavior[Command](ctx) {

  // Disclaimer:
  // Due to the compute intensiveness of traverse the actors tree,
  // we're using AbstractBehavior, mutable state and var in intention to boost our performance.

  import ctx.log

  import ActorEventsMonitorActor._

  private[this] val actorTags: mutable.Map[ActorKey, mutable.Set[Tag]] = mutable.Map.empty

  private[this] var refs: List[classic.ActorRef] = Nil

  private[this] val unbinds = mutable.Map.empty[ActorKey, Unbind]

  // start
  setTimeout()

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop | PreRestart =>
      storage.clear()
      unbinds.clear()
      actorTags.clear()
      refs = Nil
      this
  }

  def onMessage(msg: Command): Behavior[Command] = msg match {
    case UpdateActorMetrics =>
      cleanTags()
      cleanRefs()
      update()
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
   * @param storage
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
        storage = storage.save(h, collect(h))
        unbinds.remove(storage.actorToKey(h))
        val nextActors = t.prependedAll(actorTreeRunner.getChildren(h))
        traverseActorTree(nextActors)
    }

    def collect(actorRef: classic.ActorRef): ActorMetrics =
      ActorMetrics(
        mailboxSize = actorMetricsReader.mailboxSize(actorRef),
        timestamp = Timestamp.create()
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
        metrics.mailboxSize.foreach { mailboxSize =>
          log.trace("Registering a new updater for mailbox size for actor {} with value {}", key, mailboxSize)
          val tags = actorTags.get(key).fold[Set[Tag]](Set.empty)(_.toSet)
          val bind = monitor.bind(Labels(key, node, tags))
          bind.mailboxSize.setUpdater(_.observe(mailboxSize))
          unbinds.put(key, bind)
        }
    }

  private def setTimeout(): Unit = scheduler.startSingleTimer(UpdateActorMetrics, pingOffset)

}

object ActorEventsMonitorActor {

  sealed trait Command
  object Command {
    final case object UpdateActorMetrics                                                           extends Command
    final private[ActorEventsMonitorActor] case class AddTag(actorRef: classic.ActorRef, tag: Tag) extends Command
  }

  def apply(
    actorMonitor: ActorMetricMonitor,
    node: Option[Node],
    pingOffset: FiniteDuration,
    storage: ActorMetricStorage,
    streamMonitor: ActorRef[AkkaStreamMonitoring.Command],
    actorTreeRunner: ActorTreeTraverser = ReflectiveActorTreeTraverser,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { scheduler =>
        ctx.system.receptionist ! Register(tagServiceKey, ctx.messageAdapter[TagEvent] {
          case TagEvent(ref, tag) => AddTag(ref, tag)
        })

        new ActorEventsMonitorActor(
          actorMonitor,
          node,
          pingOffset,
          storage,
          ctx,
          scheduler,
          streamMonitor,
          actorTreeRunner,
          actorMetricsReader
        )
      }
    }

  trait ActorTreeTraverser {
    def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef]
    def getRootGuardian(system: classic.ActorSystem): classic.ActorRef
  }

  object ReflectiveActorTreeTraverser extends ActorTreeTraverser {
    import java.lang.invoke.MethodType.methodType

    import ReflectiveActorMonitorsUtils._

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
    def mailboxSize(actor: classic.ActorRef): Option[Int]
  }

  object ReflectiveActorMetricsReader extends ActorMetricsReader {
    import java.lang.invoke.MethodType.methodType

    import ReflectiveActorMonitorsUtils._

    private val numberOfMessagesMethodHandler = {
      val mt = methodType(classOf[Int])
      lookup.findVirtual(cellClass, "numberOfMessages", mt)
    }

    def mailboxSize(actor: classic.ActorRef): Option[Int] =
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

    def isLocalActorRefWithCell(actorRef: classic.ActorRef): Boolean =
      actorRefWithCellClass.isInstance(actorRef) &&
        isLocalMethodHandler.invoke(underlyingMethodHandler.invoke(actorRef)).asInstanceOf[Boolean]
  }

}
