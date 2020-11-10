package io.scalac.api

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.scalac.domain.{AccountStateActor, JsonCodecs, _}

import scala.language.postfixOps
import scala.util.Success

class AccountRoutes(
  shardedRef: ActorRef[ShardingEnvelope[AccountStateActor.Command]]
)(implicit val timeout: Timeout, val system: ActorSystem[Nothing])
    extends FailFastCirceSupport
    with JsonCodecs {

  val routes: Route = pathPrefix("api" / "v1" / "account" / JavaUUID) { uuid =>
    (pathPrefix("balance") & pathEndOrSingleSlash & get) {
      import AccountStateActor.Command._
      import AccountStateActor.Reply._
      onComplete(
        shardedRef.ask[AccountStateActor.Reply](
          ref => ShardingEnvelope(uuid.toString(), GetBalance(ref))
        )
      ) {
        case Success(CurrentBalance(balance)) =>
          complete(StatusCodes.OK, Account(uuid, balance))
        case _ => complete(StatusCodes.InternalServerError)
      }
    } ~ (pathPrefix("withdraw" / DoubleNumber) & pathEndOrSingleSlash) {
      amount =>
        (put | post) {
          import AccountStateActor.Command._
          import AccountStateActor.Reply._
          onComplete(
            shardedRef.ask[AccountStateActor.Reply](
              ref => ShardingEnvelope(uuid.toString(), Withdraw(ref, amount))
            )
          ) {
            case Success(CurrentBalance(balance)) =>
              complete(StatusCodes.Created, Account(uuid, balance))
            case Success(InsufficientFunds) =>
              complete(
                StatusCodes.Conflict,
                ApplicationError("insufficient funds")
              )
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
    } ~ (pathPrefix("deposit" / DoubleNumber) & pathEndOrSingleSlash) {
      amount =>
        (put | post) {
          import AccountStateActor.Command._
          import AccountStateActor.Reply._
          onComplete(
            shardedRef.ask[AccountStateActor.Reply](
              ref => ShardingEnvelope(uuid.toString(), Deposit(ref, amount))
            )
          ) {
            case Success(CurrentBalance(balance)) =>
              complete(StatusCodes.Created, Account(uuid, balance))
            case _ => complete(StatusCodes.InternalServerError)
          }
        }
    }
  }
}
