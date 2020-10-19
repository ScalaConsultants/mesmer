package io.scalac.api

import akka.actor.typed.ActorRef
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.Http
import scala.io.StdIn
import io.scalac.domain._
import java.{ util => ju }
import io.scalac.domain.JsonCodecs
import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import akka.actor.typed.ActorRef
import io.scalac.domain.AccountManagerActor
import slick.jdbc.PostgresProfile.api.Database
import com.typesafe.config.ConfigFactory
import io.scalac.infrastructure.PostgresAccountRepository
import akka.actor.typed.delivery.ConsumerController.Command
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.Behaviors
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }
import io.scalac.domain.AccountActor
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.actor.Status
import scala.concurrent.ExecutionContext
import akka.actor.typed.ActorSystem

class AccountRoutes(shardedRef: ActorRef[ShardingEnvelope[AccountActor.Command]])(
  implicit val timeout: Timeout,
  val system: ActorSystem[Nothing]
) extends FailFastCirceSupport
    with JsonCodecs {

  val routes: Route = pathPrefix("api" / "v1" / "account" / JavaUUID) { uuid =>
    (pathPrefix("balance") & pathEndOrSingleSlash & get) {
      import AccountActor.Command._
      import AccountActor.Event._
      onComplete(shardedRef.ask[AccountActor.Event](ref => ShardingEnvelope(uuid.toString(), GetBalance(ref)))) {
        case Success(CurrentBalance(balance)) => complete(StatusCodes.OK, Account(uuid, balance))
        case _                                => complete(StatusCodes.InternalServerError)
      }
    } ~ (pathPrefix("withdraw" / DoubleNumber) & pathEndOrSingleSlash) { amount =>
      (put | post) {
        import AccountActor.Command._
        import AccountActor.Event._
        onComplete(shardedRef.ask[AccountActor.Event](ref => ShardingEnvelope(uuid.toString(), Withdraw(ref, amount)))) {
          case Success(CurrentBalance(balance)) => complete(StatusCodes.Created, Account(uuid, balance))
          case Success(InsufficientFunds)       => complete(StatusCodes.Conflict, ApplicationError("insufficient funds"))
          case _                                => complete(StatusCodes.InternalServerError)
        }
      }
    } ~ (pathPrefix("deposit" / DoubleNumber) & pathEndOrSingleSlash) { amount =>
      (put | post) {
        import AccountActor.Command._
        import AccountActor.Event._
        onComplete(shardedRef.ask[AccountActor.Event](ref => ShardingEnvelope(uuid.toString(), Deposit(ref, amount)))) {
          case Success(CurrentBalance(balance)) => complete(StatusCodes.Created, Account(uuid, balance))
          case _                                => complete(StatusCodes.InternalServerError)
        }
      }
    }
  }
}
