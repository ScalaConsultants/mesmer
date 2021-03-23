package io.scalac.api

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.scalac.domain.{ AccountStateActor, JsonCodecs, _ }

import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

class AccountRoutes(
  shardedRef: ActorRef[ShardingEnvelope[AccountStateActor.Command]]
)(implicit val timeout: Timeout, val system: ActorSystem[Nothing])
    extends FailFastCirceSupport
    with JsonCodecs {

  import AccountRoutes._

  implicit val exceptionHandler: ExceptionHandler = ExceptionHandler { case e: Throwable =>
    extractUri { uri =>
      extractLog { log =>
        log.error(s"Internal Server Error for $uri", e)
        complete(StatusCodes.InternalServerError)
      }
    }
  }

  val routes: Route = Route.seal(
    pathPrefix("api" / "v1" / "account" / JavaUUID) { uuid =>
      (pathPrefix("balance") & pathEndOrSingleSlash & get) {
        import AccountStateActor.Command._
        import AccountStateActor.Reply._
        onComplete(
          shardedRef.ask[AccountStateActor.Reply](ref => ShardingEnvelope(uuid.toString, GetBalance(ref)))
        ) map { case CurrentBalance(balance) =>
          complete(StatusCodes.OK, Account(uuid, balance))
        }
      } ~
      (pathPrefix("withdraw" / DoubleNumber) & pathEndOrSingleSlash) { amount =>
        (put | post) {
          import AccountStateActor.Command._
          import AccountStateActor.Reply._
          onComplete(
            shardedRef.ask[AccountStateActor.Reply](ref => ShardingEnvelope(uuid.toString, Withdraw(ref, amount)))
          ) map {
            case CurrentBalance(balance) =>
              complete(StatusCodes.Created, Account(uuid, balance))
            case InsufficientFunds =>
              complete(StatusCodes.Conflict, ApplicationError("insufficient funds"))
          }
        }
      } ~
      (pathPrefix("deposit" / DoubleNumber) & pathEndOrSingleSlash) { amount =>
        (put | post) {
          import AccountStateActor.Command._
          import AccountStateActor.Reply._

          onComplete(
            shardedRef.ask[AccountStateActor.Reply](ref => ShardingEnvelope(uuid.toString, Deposit(ref, amount)))
          ) map { case CurrentBalance(balance) =>
            complete(StatusCodes.Created, Account(uuid, balance))
          }
        }
      }
    }
  )

}

object AccountRoutes {

  private implicit class Directive1Ops[T](val d: Directive1[Try[T]]) extends AnyVal {
    def map[U](
      handler: PartialFunction[T, StandardRoute]
    )(implicit system: ActorSystem[_]): Route =
      d.apply(mapImpl(handler))
  }

  private def mapImpl[T](
    handler: PartialFunction[T, StandardRoute]
  )(implicit system: ActorSystem[_]): Try[T] => StandardRoute = {
    case Success(value) =>
      if (handler.isDefinedAt(value)) handler(value)
      else handleException(new RuntimeException(s"unhandled pattern case for $value"))
    case Failure(e) => handleException(e)
  }

  private def handleException(e: Throwable)(implicit system: ActorSystem[_]): StandardRoute = {
    system.log.error(s"Internal Server Error: ${e.getMessage}", e)
    complete(StatusCodes.InternalServerError)
  }

}
