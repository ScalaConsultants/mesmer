package io.scalac.agent

import java.util.UUID

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

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
