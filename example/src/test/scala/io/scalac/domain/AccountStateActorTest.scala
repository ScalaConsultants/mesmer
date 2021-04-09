package io.scalac.domain

import java.util.UUID

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.persistence.testkit.PersistenceTestKitPlugin
import akka.persistence.testkit.scaladsl.PersistenceTestKit
import com.typesafe.config.Config
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

object AccountStateActorTest {
  val config: Config = PersistenceTestKitPlugin.config.withFallback(ActorTestKit.ApplicationTestConfig)
}

class AccountStateActorTest
    extends ScalaTestWithActorTestKit(AccountStateActorTest.config)
    with AnyFlatSpecLike
    with Matchers {

  import AccountStateActor.Command._
  import AccountStateActor.Event._
  import AccountStateActor.Reply
  import AccountStateActor.Reply._

  type Fixture = (UUID, TestProbe[Reply])

  val persistentTestKit: PersistenceTestKit      = PersistenceTestKit(system)
  override implicit val patience: PatienceConfig = PatienceConfig(scaled(500 millis), scaled(25 millis))
  val persistenceTimeout: FiniteDuration         = patience.timeout

  def test(test: Fixture => Any): Any = {
    val uuid  = UUID.randomUUID()
    val probe = testKit.createTestProbe[Reply]()

    test((uuid, probe))
    probe.stop()
    persistentTestKit.clearAll()
  }

  "StateActor" should "have 0.0 balance after initialized" in test { case (uuid, probe) =>
    val accountState = testKit.spawn(AccountStateActor.apply(uuid))
    accountState ! GetBalance(probe.ref)
    probe.expectMessage(CurrentBalance(0.0))
    persistentTestKit.expectNothingPersisted(uuid.toString, persistenceTimeout)
  }

  it should "not allow balance to fall below 0.0" in test { case (uuid, probe) =>
    val accountState = testKit.spawn(AccountStateActor.apply(uuid))

    accountState ! Withdraw(probe.ref, 100.0)
    accountState ! GetBalance(probe.ref)

    probe.expectMessage(InsufficientFunds)
    probe.expectMessage(CurrentBalance(0.0))
    persistentTestKit.expectNothingPersisted(uuid.toString, persistenceTimeout)
  }

  it should "return sum of all deposits" in test { case (uuid, probe) =>
    val accountState = testKit.spawn(AccountStateActor.apply(uuid))

    // don't allow it to be zero
    val deposits = List.fill(10)(Random.nextInt(100) + 1).map(_.toDouble)

    for {
      value <- deposits
    } {
      accountState ! Deposit(system.ignoreRef, value)
      persistentTestKit.expectNextPersisted(uuid.toString, MoneyDeposit(value))
    }

    accountState ! GetBalance(probe.ref)

    probe.expectMessage(CurrentBalance(deposits.sum))
  }

  it should "return current value for sequence of deposits and withdrawals" in test { case (uuid, probe) =>
    val accountState = testKit.spawn(AccountStateActor.apply(uuid))

    val deposits = List.fill(10)(Random.nextInt(100) + 1).map(_.toDouble)

    val withdraws = Random.shuffle(deposits).tail

    val depositCommands = deposits.map(value => Deposit(system.ignoreRef, value) -> MoneyDeposit(value))

    val withdrawCommands = withdraws.map(value => Withdraw(system.ignoreRef, value) -> MoneyWithdrawn(value))

    val commands = depositCommands ++ withdrawCommands

    commands.foreach { case (command, expectedEvent) =>
      accountState ! command
      persistentTestKit.expectNextPersisted(uuid.toString, expectedEvent)
    }

    accountState ! GetBalance(probe.ref)

    val expectedBalance = deposits.sum - withdraws.sum

    probe.expectMessage(CurrentBalance(expectedBalance))
  }

  it should "not persist anything when deposit / withdrawal value is 0.0" in test { case (uuid, probe) =>
    val expectedBalance = 1778.0
    val accountState    = testKit.spawn(AccountStateActor(uuid))
    accountState ! Deposit(system.ignoreRef, expectedBalance)

    accountState ! Deposit(system.ignoreRef, 0.0)
    accountState ! Withdraw(system.ignoreRef, 0.0)

    accountState ! GetBalance(probe.ref)

    probe.expectMessage(CurrentBalance(expectedBalance))
    persistentTestKit.expectNextPersisted(uuid.toString, MoneyDeposit(expectedBalance))
    persistentTestKit.expectNothingPersisted(uuid.toString, persistenceTimeout)
  }

}
