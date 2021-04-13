package io.scalac.extension.service

import akka.actor.typed._
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.{ actor => classic }
import io.scalac.core
import io.scalac.core.event.ActorEvent
import io.scalac.core.model.{ Tag, _ }
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.metric.ActorSystemMonitor.Labels
import io.scalac.extension.service.ActorTreeService.Api

import scala.collection.mutable.ArrayBuffer

object ActorTreeService {

  sealed trait Api extends Any

  sealed trait Event extends Any with Api

  sealed trait Command extends Api

  final case class GetActors(tags: Tag, reply: ActorRef[Seq[classic.ActorRef]]) extends Command

  private final case class ActorTerminated(actorRef: classic.ActorRef) extends AnyVal with Event
  private final case class ActorCreated(details: ActorRefDetails)      extends AnyVal with Event
  private final case class ActorRetagged(details: ActorRefDetails)     extends AnyVal with Event

  def apply(actorSystemMonitor: ActorSystemMonitor, node: Option[Node]): Behavior[Api] =
    Behaviors.setup { ctx =>
      def receptionistBind(actorEventRef: ActorRef[ActorEvent]): Unit =
        ctx.system.receptionist ! Register(core.actorServiceKey, actorEventRef)

      new ActorTreeService(ctx, actorSystemMonitor, receptionistBind, node).service()
    }
}

final class ActorTreeService(
  context: ActorContext[Api],
  monitor: ActorSystemMonitor,
  actorEventBind: ActorRef[ActorEvent] => Unit,
  node: Option[Node] = None
) {

  import ActorTreeService._
  import context._

  private[this] val snapshot     = ArrayBuffer.empty[ActorRefDetails]
  private[this] val boundMonitor = monitor.bind(Labels(node))

  def service(): Behavior[Api] = {

    actorEventBind(context.messageAdapter {
      case ActorEvent.ActorCreated(details) => ActorCreated(details)
      case ActorEvent.SetTags(details)      => ActorRetagged(details)
    })

    Behaviors
      .receiveMessage[Api] {
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
      .receiveSignal { case (_, PreRestart | PostStop) =>
        boundMonitor.unbind()
        Behaviors.same
      }
  }

  private def handleEvent(event: Event) = event match {
    case ActorCreated(details) =>
      import details._
      log.trace("Actor created {}", ref)
      context.watchWith(details.ref.toTyped, ActorTerminated(ref))
      boundMonitor.createdActors.incValue(1L)
      snapshot += details

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
}
