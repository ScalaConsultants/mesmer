package io.scalac.agent.utils

import java.util.UUID

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

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
