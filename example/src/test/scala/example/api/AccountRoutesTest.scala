package example.api

import java.util.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.sharding.typed.ShardingEnvelope
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

import example.domain.AccountStateActor
import example.domain.AccountStateActor.Command.Deposit
import example.domain.AccountStateActor.Command.GetBalance
import example.domain.AccountStateActor.Command.Withdraw
import example.domain.AccountStateActor.Reply.CurrentBalance
import example.domain.AccountStateActor.Reply.InsufficientFunds
import example.domain._

class AccountRoutesTest
    extends AnyFlatSpec
    with ScalatestRouteTest
    with Matchers
    with FailFastCirceSupport
    with JsonCodecs {

  implicit val typedSystem: ActorSystem[Nothing]  = system.toTyped
  implicit val timeoutDuration: FiniteDuration    = 2 seconds
  implicit val timeout: Timeout                   = timeoutDuration
  implicit val routeTimeTimeout: RouteTestTimeout = RouteTestTimeout(2 * timeoutDuration)
  import AccountStateActor.{ Command, Reply }

  override def testConfig: Config = ConfigFactory.load("application-test")

  type Fixture = (UUID, Route)

  def mockedBehaviour(
    uuid: UUID,
    receive: Command => Option[Reply]
  ): Behavior[ShardingEnvelope[AccountStateActor.Command]] = Behaviors.receiveMessage {
    case ShardingEnvelope(entity, command) =>
      assert(uuid.toString == entity)
      receive(command).foreach(command.replyTo ! _)
      Behaviors.same
  }

  def test(behaviorBody: PartialFunction[Command, Reply] = PartialFunction.empty)(testBody: Fixture => Any): Any = {
    val uuid          = UUID.randomUUID()
    val testBehavior  = mockedBehaviour(uuid, behaviorBody.lift)
    val actor         = system.spawn(testBehavior, uuid.toString)
    val accountRoutes = new AccountRoutes(actor)
    Function.untupled(testBody)(uuid, accountRoutes.routes)
    // actors should be stopped
  }

  "AccountRoutes" should "return balance for" in {
    val currentBalance = Random.nextInt(100).toDouble
    test { case GetBalance(_) =>
      CurrentBalance(currentBalance)
    } { case (uuid, routes) =>
      Get(s"/api/v1/account/$uuid/balance") ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Account] shouldEqual Account(uuid, currentBalance)
      }
    }
  }

  it should "return balance for withdrawn" in {
    val resultBalance  = Random.nextInt(100).toDouble
    val withdrawAmount = Random.nextInt(100).toDouble
    test { case Withdraw(_, value) =>
      value shouldBe withdrawAmount
      CurrentBalance(resultBalance)
    } { case (uuid, routes) =>
      Put(s"/api/v1/account/$uuid/withdraw/$withdrawAmount") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Account] shouldEqual Account(uuid, resultBalance)
      }
    }
  }

  it should "return conflict status when there is not enough funds" in {
    val withdrawAmount = Random.nextInt(100).toDouble
    test { case Withdraw(_, value) =>
      value shouldBe withdrawAmount
      InsufficientFunds
    } { case (uuid, routes) =>
      Put(s"/api/v1/account/$uuid/withdraw/$withdrawAmount") ~> routes ~> check {
        status shouldEqual StatusCodes.Conflict
      }
    }
  }

  it should "return updated account status for deposit" in {
    val depositAmount = Random.nextInt(100).toDouble
    test { case Deposit(_, value) =>
      value shouldBe depositAmount
      CurrentBalance(depositAmount)
    } { case (uuid, routes) =>
      Put(s"/api/v1/account/$uuid/deposit/$depositAmount") ~> routes ~> check {
        status shouldEqual StatusCodes.Created
        responseAs[Account] shouldEqual Account(uuid, depositAmount)
      }
    }
  }

  it should "return 500 when upstream actor doesn't respond" in test() { case (uuid, routes) =>
    Get(s"/api/v1/account/$uuid/balance") ~> routes ~> check {
      status shouldEqual StatusCodes.InternalServerError
    }
  }
}
