package io.scalac.domain
import io.scalac.serialization.SerializableMessage
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.Behaviors
import io.scalac.domain.AccountManagerActor.Command.CreateAccount
import io.scalac.domain.AccountRepository
import java.{util => ju}
import scala.util.Success
import scala.util.Failure
import akka.pattern.StatusReply

import java.io.IOException

object AccountManagerActor {

    trait Command extends SerializableMessage {
        def replyTo: ActorRef[Event]
    }

    object Command {
        final case class CreateAccount(replyTo: ActorRef[Event]) extends Command

        final case class GetAccount(replyTo: ActorRef[Event], accountId: AccountId) extends Command
    }

    trait Event extends SerializableMessage

    object Event {
        final case class AccountCreated(accountId: AccountId) extends Event

        final case class AccountFound(account: Account) extends Event

        final case object AccountNotFound extends IllegalStateException("Account not found") with Event

        final case class StorageFailed(message: String) extends IOException(message) with Event
    }

    def apply(repository: AccountRepository): Behavior[Command] = Behaviors.setup(context => {
        import AccountManagerActor.Command._
        import AccountManagerActor.Event._
        import context.executionContext
        import context.log
        Behaviors.receiveMessage {
            case CreateAccount(replyTo) => {
                log.info("Got create account message")
                val uuid = ju.UUID.randomUUID()

                repository
                    .insert(Account(uuid, 0.0))
                    .onComplete {
                        case Success(_) => 
                            replyTo ! AccountCreated(uuid)
                        case Failure(exception) => 
                            replyTo ! StorageFailed(exception.getMessage())
                    }
                Behaviors.same
            }
            case GetAccount(replyTo, accountId) => {
                repository
                    .getAccount(accountId)
                    .onComplete {
                        case Success(Some(account)) => replyTo ! AccountFound(account)
                        case Success(None) => replyTo ! AccountNotFound
                        case Failure(exception) => replyTo ! StorageFailed(exception.getMessage()) 
                    }
                Behaviors.same
            }
        }
    })
}
