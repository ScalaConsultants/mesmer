package io.scalac.domain

import java.io.IOException
import java.{util => ju}

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import io.scalac.domain.AccountActor.Event.{MoneyDeposit, MoneyWithdrawn}
import io.scalac.domain.AccountActor.Reply.{CurrentBalance, InsufficientFunds}
import io.scalac.serialization.SerializableMessage

object AccountActor {

  sealed trait Command extends SerializableMessage

  object Command {
    final case class GetBalance(replyTo: ActorRef[Reply]) extends Command

    final case class Deposit(replyTo: ActorRef[Reply], value: Double)
        extends Command

    final case class Withdraw(replyTo: ActorRef[Reply], value: Double)
        extends Command

//    private[domain] case class Started(balance: Double) extends Command
//
//    private[domain] case class StateUpdated(replyTo: ActorRef[Reply],
//                                            newBalance: Double)
//        extends Command
//
//    private[domain] case class StateUpdateFailed(replyTo: ActorRef[Reply],
//                                                 exception: Throwable)
//        extends Command
  }

  sealed trait Reply extends SerializableMessage

  object Reply {
    final case class CurrentBalance(value: Double) extends Reply
    final case object InsufficientFunds
        extends IllegalStateException("Insuficient funds")
        with Reply
    final case class PersistentStorageFailure(message: String)
        extends IOException(message)
        with Reply
  }

  sealed trait Event extends SerializableMessage

  object Event {
    final case class MoneyWithdrawn(amount: Double) extends Event
    final case class MoneyDeposit(amount: Double) extends Event
  }

  case class AccountState(balance: Double) {
    import Command._
    def commandHandler(command: Command): Effect[Event, AccountState] =
      command match {
        case GetBalance(replyTo) =>
          Effect.none.thenReply(replyTo)(state => CurrentBalance(state.balance))
        case Withdraw(replyTo, value) => {
          if (value > balance) {
            Effect.none.thenReply(replyTo)(_ => InsufficientFunds)
          } else {
            Effect
              .persist(MoneyWithdrawn(value))
              .thenReply(replyTo)(state => CurrentBalance(state.balance))
          }
        }
        case Deposit(replyTo, value) => {
          Effect
            .persist(MoneyDeposit(value))
            .thenReply(replyTo)(state => CurrentBalance(state.balance))
        }
      }

    def eventHandler(event: Event): AccountState = event match {
      case MoneyDeposit(value)   => this.copy(balance = balance + value)
      case MoneyWithdrawn(value) => this.copy(balance = balance - value)
    }
  }

  def apply(repository: AccountRepository, uuid: ju.UUID): Behavior[Command] =
    Behaviors.setup(ctx => {
      EventSourcedBehavior[Command, Event, AccountState](
        PersistenceId.ofUniqueId(uuid.toString),
        AccountState(0.0),
        (state, command) => state.commandHandler(command),
        (state, event) => state.eventHandler(event)
      )
    })

//    Behaviors.setup { context =>
//      import Command._
//      import Event._
//      import context.{executionContext, log}
//
//      context.pipeToSelf(repository.getOrCreateAccount(uuid)) {
//        case Success(account) => Started(account.balance)
//        case Failure(ex) =>
//          log.error("Account failed during initializatoin", ex)
//          throw ex
//      }
//
//      def waitForUpdate(replyTo: ActorRef[Event],
//                        newBalance: Double): Behavior[Command] = {
//        context.pipeToSelf(repository.update(Account(uuid, newBalance))) {
//          case Success(updatedValue) => StateUpdated(replyTo, updatedValue)
//          case Failure(exception)    => StateUpdateFailed(replyTo, exception)
//        }
//        waitingState
//      }
//
//      def initState = Behaviors.withStash[Command](1024) { stash =>
//        Behaviors.receiveMessage {
//          case Started(balance) => stash.unstashAll(runningState(balance))
//          case command =>
//            stash.stash(command)
//            Behaviors.same
//        }
//      }
//
//      def runningState(balance: Double): Behavior[Command] =
//        Behaviors.receiveMessage {
//          case GetBalance(replyTo) =>
//            replyTo ! CurrentBalance(balance)
//            Behaviors.same
//          case Deposit(replyTo, value) =>
//            waitForUpdate(replyTo, balance + value)
//          case Withdraw(replyTo, value) => {
//            if (balance - value < 0.0) {
//              replyTo ! InsufficientFunds
//              Behaviors.same
//            } else waitForUpdate(replyTo, balance - value)
//
//          }
//        }
//
//      def waitingState: Behavior[Command] =
//        Behaviors.withStash(1024)(
//          stash =>
//            Behaviors.receiveMessage {
//              case StateUpdated(replyTo, newState) => {
//                replyTo ! CurrentBalance(newState)
//                runningState(newState)
//              }
//              case StateUpdateFailed(replyTo, exception) => {
//                replyTo ! PersistentStorageFailure(exception.getMessage())
//                //default behaviour for sharded supervisor is to restart actor
//                throw exception
//              }
//              case command =>
//                stash.stash(command)
//                Behaviors.same
//          }
//        )
//      initState
//    }
}
