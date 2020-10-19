package io.scalac.domain

import akka.actor.typed.Behavior._
import io.scalac.serialization.SerializableMessage
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import java.{ util => ju }
import io.scalac.domain.AccountActor.Command.GetBalance
import cats.syntax.representable
import scala.util.{ Failure, Success }
import akka.actor.typed.delivery.ConsumerController.Start
import java.io.IOException

object AccountActor {

  trait Command extends SerializableMessage

  object Command {
    final case class GetBalance(replyTo: ActorRef[Event]) extends Command

    final case class Deposit(replyTo: ActorRef[Event], value: Double) extends Command

    final case class Withdraw(replyTo: ActorRef[Event], value: Double) extends Command

    private[domain] case class Started(balance: Double) extends Command

    private[domain] case class StateUpdated(replyTo: ActorRef[Event], newBalance: Double) extends Command

    private[domain] case class StateUpdateFailed(replyTo: ActorRef[Event], exception: Throwable) extends Command
  }

  trait Event extends SerializableMessage

  object Event {
    final case class CurrentBalance(value: Double)             extends Event
    final case object InsufficientFunds                        extends IllegalStateException("Insuficient funds") with Event
    final case class PersistentStorageFailure(message: String) extends IOException(message) with Event
  }

  def apply(repository: AccountRepository, uuid: ju.UUID): Behavior[Command] = Behaviors.setup { context =>
    import Command._
    import Event._
    import context.log
    import context.executionContext

    context.pipeToSelf(repository.getOrCreateAccount(uuid)) {
      case Success(account) => Started(account.balance)
      case Failure(ex) =>
        log.error("Account failed during initializatoin", ex)
        throw ex
    }

    def waitForUpdate(replyTo: ActorRef[Event], newBalance: Double): Behavior[Command] = {
      context.pipeToSelf(repository.update(Account(uuid, newBalance))) {
        case Success(updatedValue) => StateUpdated(replyTo, updatedValue)
        case Failure(exception)    => StateUpdateFailed(replyTo, exception)
      }
      waitingState
    }

    def initState = Behaviors.withStash[Command](1024) { stash =>
      Behaviors.receiveMessage {
        case Started(balance) => stash.unstashAll(runningState(balance))
        case command =>
          stash.stash(command)
          Behaviors.same
      }
    }

    def runningState(balance: Double): Behavior[Command] = Behaviors.receiveMessage {
      case GetBalance(replyTo) =>
        replyTo ! CurrentBalance(balance)
        Behaviors.same
      case Deposit(replyTo, value) => waitForUpdate(replyTo, balance + value)
      case Withdraw(replyTo, value) => {
        if (balance - value < 0.0) {
          replyTo ! InsufficientFunds
          Behaviors.same
        } else waitForUpdate(replyTo, balance - value)

      }
    }

    def waitingState: Behavior[Command] =
      Behaviors.withStash(1024)(stash =>
        Behaviors.receiveMessage {
          case StateUpdated(replyTo, newState) => {
            replyTo ! CurrentBalance(newState)
            runningState(newState)
          }
          case StateUpdateFailed(replyTo, exception) => {
            replyTo ! PersistentStorageFailure(exception.getMessage())
            //default behaviour for sharded supervisor is to restart actor
            throw exception
          }
          case command =>
            stash.stash(command)
            Behaviors.same
        }
      )
    initState
  }
}
