package io.scalac.extension

import scala.collection.immutable

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.{ typed, ActorRef, ActorRefProvider, ActorSystem }

import org.slf4j.LoggerFactory

import io.scalac.extension.config.CachingConfig
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.{ ActorMetricMonitor, Asynchronized, CachingMonitor }
import io.scalac.extension.model.Node

class ActorEventsMonitor
object ActorEventsMonitor {

  private val logger = LoggerFactory.getLogger(getClass)

  def start(
    actorMonitor: ActorMetricMonitor with Asynchronized,
    actorSystem: typed.ActorSystem[_],
    node: Option[Node],
    actorTreeRunner: ActorTreeRunner = ReflectiveActorTreeRunner,
    actorMetricsReader: ActorMetricsReader = ReflectiveActorMetricsReader
  ): Unit = {

    import actorSystem.{ classicSystem, settings }

    val monitor = CachingMonitor(actorMonitor, CachingConfig.fromConfig(settings.config, "actor"))

    def runActorTree(root: ActorRef): Unit = {
      report(root)
      actorTreeRunner.getChildren(root).foreach(runActorTree)
    }

    def report(actor: ActorRef): Unit = {
      val bound = monitor.bind(Labels(actor.path.toStringWithoutAddress, node))

      // mailbox
      actorMetricsReader.mailboxSize(actor).foreach { mailboxSize =>
        bound.mailboxSize.setValue(mailboxSize)
        logger.trace(s"Recorded mailbox size for actor $actor: $mailboxSize")
      }

      // ...
    }

    actorMonitor.onCollect {
      runActorTree(actorTreeRunner.getRootGuardian(classicSystem))
    }

  }

  trait ActorTreeRunner {
    def getChildren(actor: ActorRef): Iterable[ActorRef]
    def getRootGuardian(system: ActorSystem): ActorRef
  }

  object ReflectiveActorTreeRunner extends ActorTreeRunner {
    import Utils._

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
    import Utils._

    private val getNumberOfMessagesReflectively = cellClass.getDeclaredMethod("numberOfMessages")

    def mailboxSize(actor: ActorRef): Option[Int] =
      if (isLocalActorRefWithCell(actor)) {
        val cell = underlyingReflectively.invoke(actor)
        Some(getNumberOfMessagesReflectively.invoke(cell).asInstanceOf[Int])
      } else None

  }

  private object Utils {
    private[ActorEventsMonitor] val actorRefWithCellClass  = Class.forName("akka.actor.ActorRefWithCell")
    private[ActorEventsMonitor] val cellClass              = Class.forName("akka.actor.Cell")
    private[ActorEventsMonitor] val underlyingReflectively = actorRefWithCellClass.getDeclaredMethod("underlying")
    private val isLocalReflectively                        = cellClass.getDeclaredMethod("isLocal")
    def isLocalActorRefWithCell(actorRef: ActorRef): Boolean =
      actorRefWithCellClass.isInstance(actorRef) &&
        isLocalReflectively.invoke(underlyingReflectively.invoke(actorRef)).asInstanceOf[Boolean]
  }

  sealed trait Command
  case class ActorEventWrapper(actorEvent: ActorEvent) extends Command
  object Command {
    def receiveMessage(f: ActorEvent => Behavior[Command]): Behavior[Command] = Behaviors.receiveMessage[Command] {
      case ActorEventWrapper(actorEvent) => f(actorEvent)
    }
  }

  object Actor {
    def apply(monitor: ActorMetricMonitor, node: Option[Node]): Behavior[Command] =
      Behaviors.setup[Command](ctx => new Actor(monitor, node, ctx).start())
  }
  class Actor(monitor: ActorMetricMonitor, node: Option[Node], ctx: ActorContext[Command]) {

    import ctx.{ log, messageAdapter, system }

    Receptionist(system).ref ! Register(
      actorServiceKey,
      messageAdapter(ActorEventWrapper.apply)
    )

    def start(): Behavior[Command] = Command.receiveMessage {
      case StashMeasurement(size, path) =>
        log.trace(s"Recorded stash size for actor $path: $size")
        monitor.bind(Labels(path, node)).stashSize.setValue(size)
        Behaviors.same
    }
  }

}
