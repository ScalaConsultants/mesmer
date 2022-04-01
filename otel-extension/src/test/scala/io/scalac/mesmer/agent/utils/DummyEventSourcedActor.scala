package io.scalac.mesmer.agent.utils

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}

import java.util.UUID

object DummyEventSourcedActor {

  sealed trait Command

  final case object DoNothing extends Command
  final case object Persist   extends Command

  final case object OneInc

  def apply(uuid: UUID, snapshotEvery: Int = 1): Behavior[Command] =
    Behaviors.setup { _ =>
      EventSourcedBehavior[Command, OneInc.type, Int](
        PersistenceId.ofUniqueId(uuid.toString),
        0,
        (_, cmd) =>
          cmd match {
            case DoNothing => Effect.none
            case Persist   => Effect.persist(OneInc)
          },
        (current, event) =>
          event match {
            case OneInc => current + 1
          }
      ).withRetention(RetentionCriteria.snapshotEvery(snapshotEvery, 1))
    }
}
