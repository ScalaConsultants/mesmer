package io.scalac.agent.akka.actor

import scala.concurrent.duration._

import akka.actor.ActorPath
import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.{ actor => classic }

import org.scalatest.flatspec.AnyFlatSpecLike

import io.scalac.extension.actorServiceKey
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.util.ReceptionistOps

class AkkaActorAgentTest
    extends ScalaTestWithActorTestKit(classic.ActorSystem("AkkaActorAgentTest").toTyped)
    with AnyFlatSpecLike
    with ReceptionistOps {

  import AkkaActorAgentTest._

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[ActorEvent]
    Receptionist(system).ref ! Register(actorServiceKey, monitor.ref)
    onlyRef(monitor.ref, actorServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(actorServiceKey, monitor.ref)
  }

  "AkkaActorAgent" should "record classic stash properly" in test { monitor =>
    val stashActor = system.systemActorOf(
      Behaviors.setup[String] { ctx =>
        val classic = ctx.actorOf(ClassicStashActor.props(), "stashActor")
        Behaviors.receiveMessage { msg =>
          classic ! msg
          Behaviors.same
        }
      },
      "classic"
    )
    val expectStashSize: Int => Unit = createExpectStashSize(monitor, "/system/classic/stashActor")
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

  it should "record typed stash properly" in test { monitor =>
    val stashActor                   = system.systemActorOf(TypedStash(10), "typedStashActor")
    val expectStashSize: Int => Unit = createExpectStashSize(monitor, stashActor)
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

  def createExpectStashSize[T <: { def path: ActorPath }](monitor: Fixture, ref: T): Int => Unit =
    createExpectStashSize(monitor, ref.path.toStringWithoutAddress)

  def createExpectStashSize(monitor: Fixture, path: String): Int => Unit = { size =>
    val msg = monitor.fishForMessage(2.seconds) {
      case StashMeasurement(`size`, `path`) => FishingOutcome.Complete
      case _                                => FishingOutcome.ContinueAndIgnore
    }
    msg.size should not be (0)
  }

}

object AkkaActorAgentTest {

  import scala.language.reflectiveCalls

  type Fixture = TestProbe[ActorEvent]

  object ClassicStashActor {
    def props(): classic.Props = classic.Props(new ClassicStashActor)
  }
  class ClassicStashActor extends classic.Actor with classic.Stash {
    def receive: Receive = {
      case "open" =>
        unstashAll()
        context
          .become({
            case "close" =>
              context.unbecome()
            case msg =>
              println(s"[working on] $msg")
          })
      case msg =>
        println(s"[stash] $msg")
        stash()
    }
  }

  object TypedStash {
    def apply(capacity: Int): Behavior[String] =
      Behaviors.withStash(capacity)(buffer => new TypedStash(buffer).closed())
  }

  class TypedStash(buffer: StashBuffer[String]) {
    private def closed(): Behavior[String] =
      Behaviors.receiveMessage {
        case "open" =>
          buffer.unstashAll(open())
        case msg =>
          println(s"[typed] [stashing] $msg")
          buffer.stash(msg)
          Behaviors.same
      }
    private def open(): Behavior[String] = Behaviors.receiveMessage {
      case "close" =>
        closed()
      case msg =>
        println(s"[typed] [working on] $msg")
        Behaviors.same
    }

  }

}
