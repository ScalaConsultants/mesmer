package io.scalac.domain

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{ Effect, EventSourcedBehavior, RetentionCriteria }
import io.scalac.domain.AccountStateActor.Event.{ MoneyDeposit, MoneyWithdrawn }
import io.scalac.domain.AccountStateActor.Reply.{ CurrentBalance, InsufficientFunds }
import io.scalac.serialization.SerializableMessage

import java.io.IOException
import java.{ util => ju }

object AccountStateActor {

  sealed trait Command extends SerializableMessage {
    def replyTo: ActorRef[Reply]
  }

  object Command {
    final case class GetBalance(override val replyTo: ActorRef[Reply]) extends Command

    final case class Deposit(override val replyTo: ActorRef[Reply], value: Double) extends Command

    final case class Withdraw(override val replyTo: ActorRef[Reply], value: Double) extends Command
  }

  sealed trait Reply extends SerializableMessage

  object Reply {
    final case class CurrentBalance(value: Double)             extends Reply
    final case object InsufficientFunds                        extends IllegalStateException("Insufficient funds") with Reply
    final case class PersistentStorageFailure(message: String) extends IOException(message) with Reply
  }

  sealed trait Event extends SerializableMessage

  object Event {
    final case class MoneyWithdrawn(amount: Double) extends Event
    final case class MoneyDeposit(amount: Double)   extends Event
  }

  final case class AccountState(balance: Double) extends SerializableMessage {
    import Command._
    def commandHandler(command: Command): Effect[Event, AccountState] =
      command match {
        case GetBalance(replyTo) =>
          Effect.none.thenReply(replyTo)(state => CurrentBalance(state.balance))
        case Withdraw(replyTo, value) => {
          if (value < balance && value > 0.0) {
            Effect
              .persist(MoneyWithdrawn(value))
              .thenReply(replyTo)(state => CurrentBalance(state.balance))
          } else {
            Effect.none.thenReply(replyTo)(_ => InsufficientFunds)
          }
        }
        case Deposit(replyTo, value) => {
          val effect = if (value > 0.0) {
            Effect.persist[Event, AccountState](MoneyDeposit(value))
          } else Effect.none[Event, AccountState]

          effect
            .thenReply(replyTo)(state => CurrentBalance(state.balance))
        }
      }

    def eventHandler(event: Event): AccountState = event match {
      case MoneyDeposit(value)   => this.copy(balance = balance + value)
      case MoneyWithdrawn(value) => this.copy(balance = balance - value)
    }
  }

  def apply(uuid: ju.UUID, snapshotEvery: Int = 10, keepSnapshots: Int = 2): Behavior[Command] =
    Behaviors.setup { ctx =>
      EventSourcedBehavior[Command, Event, AccountState](
        PersistenceId.ofUniqueId(uuid.toString),
        AccountState(0.0),
        (state, command) => state.commandHandler(command),
        (state, event) => state.eventHandler(event)
      ).withRetention(RetentionCriteria.snapshotEvery(snapshotEvery, keepSnapshots))

    }
}
