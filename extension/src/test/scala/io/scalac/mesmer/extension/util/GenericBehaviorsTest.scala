package io.scalac.mesmer.extension.util

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.util.Random

import io.scalac.mesmer.core.util.ReceptionistOps
import io.scalac.mesmer.core.util.TestBehaviors
import io.scalac.mesmer.core.util.TestCase.NoSetupTestCaseFactory
import io.scalac.mesmer.core.util.TestCase.ProvidedActorSystemTestCaseFactory
import io.scalac.mesmer.core.util.TestConfig
import io.scalac.mesmer.extension.util.GenericBehaviorsTest.Command
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.CounterCommand
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.Dec
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.Inc

object GenericBehaviorsTest {
  sealed trait Command
}

class GenericBehaviorsTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with ProvidedActorSystemTestCaseFactory
    with NoSetupTestCaseFactory
    with ReceptionistOps {

  protected def createContext(env: ActorSystem[_]): Unit = ()

  type Context = Unit

  val TestServiceKey: ServiceKey[Command]  = ServiceKey[Command]("test_service")
  val DummyServiceKey: ServiceKey[Command] = ServiceKey[Command]("dummy_service_key")

  override def setUp(context: Context): Unit = {
    killServices(TestServiceKey)
    noServices(TestServiceKey)
  }

  private def publishServices(count: Int): Seq[ActorRef[Command]] = {
    val serviceRef = List.fill(count)(system.systemActorOf(Behaviors.empty[Command], createUniqueId))
    serviceRef.foreach(ref => system.receptionist ! Register(TestServiceKey, ref))
    serviceRef
  }

  "WaitForService" should "transition to next state" in testCase { _ =>
    val monitorProbe = TestProbe[ActorRef[_]]()
    val generic = GenericBehaviors.waitForService(TestServiceKey) { ref =>
      Behaviors.setup[Command] { _ =>
        monitorProbe.ref ! ref
        Behaviors.ignore
      }
    }

    val serviceRef = publishServices(1).loneElement
    system.systemActorOf(generic, createUniqueId)

    monitorProbe.receiveMessage() should be(serviceRef)
  }

  it should "publish one of possible services" in testCase { _ =>
    val monitorProbe = TestProbe[ActorRef[_]]()
    val generic = GenericBehaviors.waitForService(TestServiceKey) { ref =>
      Behaviors.setup[Command] { _ =>
        monitorProbe.ref ! ref
        Behaviors.ignore
      }
    }

    val refs = publishServices(10)
    system.systemActorOf(generic, createUniqueId)

    refs should contain(monitorProbe.receiveMessage())
  }

  it should "receive messages inner behavior protocol" in testCase { _ =>
    val CommandsCount = 100
    val monitorProbe  = TestProbe[CounterCommand]()
    val generic = GenericBehaviors.waitForService(TestServiceKey) { _ =>
      TestBehaviors.Pass.toRef(monitorProbe.ref)
    }
    val commands = List
      .fill(CommandsCount) {
        val number = Random.nextInt(100)
        if (Random.nextBoolean()) Inc(number) else Dec(number)
      }

    publishServices(10)
    val sut = system.systemActorOf(generic, createUniqueId)
    commands.foreach(sut.tell)

    monitorProbe.receiveMessages(CommandsCount) should contain theSameElementsAs (commands)
    monitorProbe.expectNoMessage()
  }

  it should "stash messages until services are published" in testCase { _ =>
    val CommandsCount = 100
    val monitorProbe  = TestProbe[CounterCommand]()
    val generic = GenericBehaviors.waitForService(TestServiceKey) { _ =>
      TestBehaviors.Pass.toRef(monitorProbe.ref)
    }
    val commands = List
      .fill(CommandsCount) {
        val number = Random.nextInt(100)
        if (Random.nextBoolean()) Inc(number) else Dec(number)
      }

    val sut = system.systemActorOf(generic, createUniqueId)
    commands.foreach(sut.tell)

    monitorProbe.expectNoMessage()

    publishServices(10)

    monitorProbe.receiveMessages(CommandsCount) should contain theSameElementsAs (commands)
  }

  it should "timeout after 2 seconds" in testCase { _ =>
    val monitorProbe = TestProbe[CounterCommand]()
    val failedProbe  = TestProbe[CounterCommand]()
    val timeout      = 1.second

    val generic = GenericBehaviors.waitForServiceWithTimeout(DummyServiceKey, timeout)(
      _ => TestBehaviors.Pass.toRef(monitorProbe.ref),
      TestBehaviors.Pass.toRef(failedProbe.ref)
    )

    val sut = system.systemActorOf(generic, createUniqueId)

    sut ! Inc(19L)

    failedProbe.receiveMessage(timeout * 2) should be(Inc(19L))
    monitorProbe.expectNoMessage(timeout * 2)
  }
}
