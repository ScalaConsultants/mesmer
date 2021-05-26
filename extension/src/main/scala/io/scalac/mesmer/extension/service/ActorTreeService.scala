package io.scalac.mesmer.extension.service

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.{ actor => classic }

import scala.collection.mutable.ArrayBuffer

import io.scalac.mesmer.core
import io.scalac.mesmer.core.event.ActorEvent
import io.scalac.mesmer.core.model.Tag
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.metric.ActorSystemMonitor
import io.scalac.mesmer.extension.metric.ActorSystemMonitor.Labels
import io.scalac.mesmer.extension.service.ActorTreeService.Api
import io.scalac.mesmer.extension.util.Tree.Tree

object ActorTreeService {

  sealed trait Api extends Any

  sealed trait Event extends Any with Api

  sealed trait Command extends Api

  object Command {

    final case class GetActors(tags: Tag, reply: ActorRef[Seq[classic.ActorRef]]) extends Command

    final case class GetActorTree(tags: Tag, reply: ActorRef[Tree[classic.ActorRef]])
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

      new ActorTreeService(ctx, actorSystemMonitor, receptionistBind, backoffActorTreeTraverser, node)
    }
}

final class ActorTreeService(
  ctx: ActorContext[Api],
  monitor: ActorSystemMonitor,
  actorEventBind: ActorRef[ActorEvent] => Unit,
  actorTreeTraverser: ActorTreeTraverser,
  node: Option[Node] = None
) extends AbstractBehavior[Api](ctx) {
  import ActorTreeService._
  import Command._
  import Event._

  import ActorTreeService._
  import context._

  private[this] val snapshot     = ArrayBuffer.empty[ActorRefDetails]
  private[this] val boundMonitor = monitor.bind(Labels(node))

  private def init(): Unit = {
    actorEventBind(context.messageAdapter {
      case ActorEvent.ActorCreated(details) => ActorCreated(details)
      case ActorEvent.TagsSet(details)      => ActorRetagged(details)
    })

    actorTreeTraverser
      .getActorTreeFromRootGuardian(system.toClassic)
      .foreach { ref =>
        handleEvent(ActorCreated(ActorRefDetails(ref, Set.empty)))
      }
  }

  def onMessage(msg: Api): Behavior[Api] = msg match {
    case GetActors(Tag.all, reply) =>
      reply ! snapshot.toSeq.map(_.ref)
      Behaviors.same
    case GetActors(tag, reply) =>
      reply ! snapshot.filter(_.tags.contains(tag)).toSeq.map(_.ref)
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

      if (!snapshot.exists(_.ref == ref)) { // deduplication
        context.watchWith(details.ref.toTyped, ActorTerminated(ref))
        boundMonitor.createdActors.incValue(1L)
        snapshot += details
      }

    case ActorRetagged(details) =>
      import details._
      log.trace("Actor retagged {}", ref)

      val index = snapshot.indexWhere(_.ref == details.ref)
      if (index >= 0) {
        snapshot.patchInPlace(index, Seq(details), 1)
      }

    case ActorTerminated(ref) =>
      log.trace("Actor terminated {}", ref)
      boundMonitor.terminatedActors.incValue(1L)
      snapshot.filterInPlace(_.ref != ref)
  }

  // bind to actor event stream
  init()
}
