package io.scalac.agent

import java.util.UUID

import _root_.akka.actor.typed.scaladsl.Behaviors
import _root_.akka.actor.typed.{ ActorRef, Behavior }
import _root_.akka.persistence.typed.PersistenceId
import _root_.akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

object DummyEventsourcedActor {

  final case class Command(replyTo: ActorRef[Unit])

  def apply(uuid: UUID): Behavior[Command] =
    Behaviors.setup { _ =>
      EventSourcedBehavior[Command, Unit, Unit](
        PersistenceId.ofUniqueId(uuid.toString),
        (),
        (_, cmd) => Effect.reply(cmd.replyTo)(()),
        (_, _) => Effect.none
      )
    }
}
