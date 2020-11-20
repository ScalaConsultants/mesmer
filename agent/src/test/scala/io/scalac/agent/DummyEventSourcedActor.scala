package io.scalac.agent

import java.util.UUID

import _root_.akka.actor.typed.scaladsl.Behaviors
import _root_.akka.actor.typed.{ ActorRef, Behavior }
import _root_.akka.persistence.typed.PersistenceId
import _root_.akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior }

object DummyEventSourcedActor {

  final case object Command

  def apply(uuid: UUID): Behavior[Command.type] =
    Behaviors.setup { _ =>
      EventSourcedBehavior[Command.type, Unit, Unit](
        PersistenceId.ofUniqueId(uuid.toString),
        (),
        (_, cmd) => Effect.none,
        (_, _) => ()
      )
    }
}
