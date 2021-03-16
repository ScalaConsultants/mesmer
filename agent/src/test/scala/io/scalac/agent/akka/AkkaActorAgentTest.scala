package io.scalac.agent.akka

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior }
import akka.{ actor => classic }
import io.scalac.agent.utils.InstallAgent
import io.scalac.core.model._
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.actorServiceKey
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.util.ReceptionistOps
import org.scalatest.flatspec.AnyFlatSpecLike

import scala.concurrent.duration._

class AkkaActorAgentTest
    extends ScalaTestWithActorTestKit(classic.ActorSystem("AkkaActorAgentTest").toTyped)
    with AnyFlatSpecLike
    with ReceptionistOps
    with InstallAgent {

  import AkkaActorAgentTest._

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[ActorEvent]
    Receptionist(system).ref ! Register(actorServiceKey, monitor.ref)
    onlyRef(monitor.ref, actorServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(actorServiceKey, monitor.ref)
    monitor.stop()
  }

  "AkkaActorAgent" should "record classic stash properly" in test { monitor =>
    val stashActor                   = system.classicSystem.actorOf(ClassicStashActor.props(), "stashActor")
    val expectStashSize: Int => Unit = createExpectStashSize(monitor, "/user/stashActor")
    stashActor ! Message("random")
    expectStashSize(1)
    stashActor ! Message("42")
    expectStashSize(2)
    stashActor ! Open
    expectStashSize(0)
    stashActor ! Message("normal")
    monitor.expectNoMessage()
    stashActor ! Close
    stashActor ! Message("emanuel")
    expectStashSize(1)
  }

  it should "record typed stash properly" in test { monitor =>
    val stashActor                   = system.systemActorOf(TypedStash(10), "typedStashActor")
    val expectStashSize: Int => Unit = createExpectStashSize(monitor, stashActor)
    stashActor ! Message("random")
    expectStashSize(1)
    stashActor ! Message("42")
    expectStashSize(2)
    stashActor ! Open
    expectStashSize(0)
    stashActor ! Message("normal")
    monitor.expectNoMessage()
    stashActor ! Close
    stashActor ! Message("emanuel")
    expectStashSize(1)
  }

  def createExpectStashSize(monitor: Fixture, ref: ActorRef[_]): Int => Unit =
    createExpectStashSize(monitor, ActorPathOps.getPathString(ref))

  def createExpectStashSize(monitor: Fixture, path: ActorPath): Int => Unit = { size =>
    val msg = monitor.fishForMessage(2.seconds) {
      case StashMeasurement(`size`, `path`) => FishingOutcome.Complete
      case _                                => FishingOutcome.ContinueAndIgnore
    }
    msg.size should not be (0)
  }

}

object AkkaActorAgentTest {

  type Fixture = TestProbe[ActorEvent]

  sealed trait Command
  final case object Open                 extends Command
  final case object Close                extends Command
  final case class Message(text: String) extends Command

  object ClassicStashActor {
    def props(): classic.Props = classic.Props(new ClassicStashActor)
  }
  class ClassicStashActor extends classic.Actor with classic.Stash with classic.ActorLogging {
    def receive: Receive = {
      case Open =>
        unstashAll()
        context
          .become({
            case Close =>
              context.unbecome()
            case Message(text) =>
              log.warning(s"[working on] {}", text)
          })
      case Message(text) =>
        log.warning(s"[stash] {}", text)
        stash()
    }
  }

  object TypedStash {
    def apply(capacity: Int): Behavior[Command] =
      Behaviors.setup(ctx => Behaviors.withStash(capacity)(buffer => new TypedStash(ctx, buffer).closed()))
  }

  class TypedStash(ctx: ActorContext[Command], buffer: StashBuffer[Command]) {
    import ctx.log
    private def closed(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case message @ Message(text) =>
          log.warn(s"[typed] [stashing] {}", text)
          buffer.stash(message)
          Behaviors.same
      }
    private def open(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Close =>
          closed()
        case Message(text) =>
          log.warn(s"[typed] [working on] {}", text)
          Behaviors.same
      }

  }

}
