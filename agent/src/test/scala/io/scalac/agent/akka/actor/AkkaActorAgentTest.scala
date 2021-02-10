package io.scalac.agent.akka.actor

import akka.actor.ActorLogging
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.{ actor => classic }
import akka.actor.typed.scaladsl.adapter._

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.dynamic.scaffold.TypeValidation
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike

import io.scalac.extension.actorServiceKey
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.util.ReceptionistOps

class AkkaActorAgentTest
    extends ScalaTestWithActorTestKit(classic.ActorSystem("AkkaActorAgentTest").toTyped)
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with ReceptionistOps {

  import AkkaActorAgentTest._

  override def beforeAll(): Unit = {
    super.beforeAll()
    val instrumentation = ByteBuddyAgent.install()
    val builder = new AgentBuilder.Default()
      .`with`(new ByteBuddy())
    val modules = Map(AkkaActorAgent.moduleName -> AkkaActorAgent.defaultVersion)
    AkkaActorAgent.agent.installOn(builder, instrumentation, modules)
  }

  type Fixture = TestProbe[ActorEvent]

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[ActorEvent]
    Receptionist(system).ref ! Register(actorServiceKey, monitor.ref)
    onlyRef(monitor.ref, actorServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(actorServiceKey, monitor.ref)
  }

  "AkkaActorAgent" should "record stash properly" in test { monitor =>
    val stashActor = system.classicSystem.actorOf(StashActor.props())

    def expectStashSize(size: Int): Unit =
      monitor.expectMessage(StashMeasurement(size, stashActor.path.toStringWithoutAddress))

    stashActor ! "random"
    expectStashSize(1)
    stashActor ! "42"
    expectStashSize(2)
    stashActor ! "open"
    expectStashSize(0)
    stashActor ! "close"
    stashActor ! "emanuel"
    expectStashSize(1)
  }

}

object AkkaActorAgentTest {

  object StashActor {
    def props(): classic.Props = classic.Props(new StashActor)
  }
  class StashActor extends classic.Actor with classic.Stash {
    def receive: Receive = {
      case "open" =>
        println(s"[open] unstashing")
        unstashAll()
        println(s"[open] becoming")
        context
          .become({
            case "close" =>
              println(s"[open] unbecoming")
              context.unbecome()
            case msg =>
              println(s"[working on] $msg")
          })
      case msg =>
        println(s"[stash] $msg")
        stash()
    }
  }

}
