package io.scalac.mesmer.extension.service

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext, Behaviors }
import akka.{ actor => classic }
import io.scalac.mesmer.core
import io.scalac.mesmer.core.event.ActorEvent
import io.scalac.mesmer.core.model.{ Tag, _ }
import io.scalac.mesmer.extension.metric.ActorSystemMonitor
import io.scalac.mesmer.extension.metric.ActorSystemMonitor.Labels
import io.scalac.mesmer.extension.service.ActorTreeService.Api
import io.scalac.mesmer.extension.util.Tree.Tree
import io.scalac.mesmer.extension.util._

object ActorTreeService {

  sealed trait Api extends Any

  sealed trait Event extends Any with Api

  sealed trait Command extends Api

  object Command {

    final case class GetActors(tags: Tag, reply: ActorRef[Seq[classic.ActorRef]]) extends Command

    final case class GetActorTree(reply: ActorRef[Tree[ActorRefDetails]]) extends Command

  }

  object Event {

    private[ActorTreeService] final case class ActorTerminated(actorRef: classic.ActorRef) extends AnyVal with Event
    private[ActorTreeService] final case class ActorCreated(details: ActorRefDetails)      extends AnyVal with Event
    private[ActorTreeService] final case class ActorRetagged(details: ActorRefDetails)     extends AnyVal with Event

  }

  def apply(
    actorSystemMonitor: ActorSystemMonitor,
    node: Option[Node],
    backoffActorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser
  ): Behavior[Api] =
    Behaviors.setup { ctx =>
      def receptionistBind(actorEventRef: ActorRef[ActorEvent]): Unit =
        ctx.system.receptionist ! Register(core.actorServiceKey, actorEventRef)

      new ActorTreeService(ctx, actorSystemMonitor, receptionistBind, backoffActorTreeTraverser, node)(partialOrdering)
    }

  lazy val partialOrdering: PartialOrdering[classic.ActorRef] = new PartialOrdering[classic.ActorRef] {
    private def actorLevel(ref: classic.ActorRef): Int = ref.path.toStringWithoutAddress.count(_ == '/')

    def tryCompare(x: classic.ActorRef, y: classic.ActorRef): Option[Int] =
      (x.path.toStringWithoutAddress, y.path.toStringWithoutAddress) match {
        case (xPath, yPath) if xPath == yPath          => Some(0)
        case (xPath, yPath) if xPath.startsWith(yPath) => Some(1)
        case (xPath, yPath) if yPath.startsWith(xPath) => Some(-1)
        case _                                         => None
      }

    def lteq(x: classic.ActorRef, y: classic.ActorRef): Boolean = actorLevel(x) <= actorLevel(y)
  }

}

final class ActorTreeService(
  ctx: ActorContext[Api],
  monitor: ActorSystemMonitor,
  actorEventBind: ActorRef[ActorEvent] => Unit,
  actorTreeTraverser: ActorTreeTraverser,
  node: Option[Node] = None
)(implicit actorRefPartialOrdering: PartialOrdering[classic.ActorRef])
    extends AbstractBehavior[Api](ctx) {
  import ActorTreeService._
  import Command._
  import Event._
  import ActorTreeService._
  import context._

//  private[this] val snapshot     = ArrayBuffer.empty[ActorRefDetails]
  private[this] val snapshot     = Tree.builder[classic.ActorRef, ActorRefDetails]
  private[this] val boundMonitor = monitor.bind(Labels(node))

  private def init(): Unit = {
    actorEventBind(context.messageAdapter {
      case ActorEvent.ActorCreated(details) => ActorCreated(details)
      case ActorEvent.TagsSet(details)      => ActorRetagged(details)
    })

    actorTreeTraverser
      .getActorTreeFromRootGuardian(system.toClassic)
      .foreach { ref =>
        //TODO hardcoded configuration
        handleEvent(ActorCreated(ActorRefDetails(ref, Set.empty, ActorConfiguration.instanceConfig)))
      }
  }

  def onMessage(msg: Api): Behavior[Api] = msg match {
    case GetActors(Tag.all, reply) =>
      reply ! snapshot.buildSeq((ref, _) => Some(ref))
      Behaviors.same
    case GetActors(tag, reply) =>
      reply ! snapshot.buildSeq((ref, details) => if (details.tags.contains(tag)) Some(ref) else None)
      Behaviors.same
    case GetActorTree(reply) =>
      //TODO change this to be more secure
      snapshot.buildTree((_, details) => Some(details)).foreach(reply ! _)
      Behaviors.same
    case event: Event =>
      handleEvent(event)
      Behaviors.same
  }

  override def onSignal: PartialFunction[Signal, Behavior[Api]] = {
    case PreRestart =>
      log.error("Restarting actor")
      boundMonitor.unbind()
      Behaviors.same
    case PostStop =>
      log.info("Actor stopped")
      boundMonitor.unbind()
      Behaviors.same
  }

  private def handleEvent(event: Event) = event match {
    case ActorCreated(details) =>
      import details._
      log.trace("Actor created {}", ref)

      context.watchWith(details.ref.toTyped, ActorTerminated(ref))
      boundMonitor.createdActors.incValue(1L)
      snapshot.insert(ref, details)

    case ActorRetagged(details) =>
      import details._
      log.trace("Actor retagged {}", ref)
      snapshot.modify(ref, _ => details)

    case ActorTerminated(ref) =>
      log.trace("Actor terminated {}", ref)
      boundMonitor.terminatedActors.incValue(1L)
      snapshot.remove(ref)
  }

  // bind to actor event stream
  init()
}
