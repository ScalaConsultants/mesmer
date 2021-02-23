package io.scalac.agent.akka.actor

import scala.concurrent.duration._

import akka.actor.ActorPath
import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.{ actor => classic }

import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AnyFlatSpecLike

import io.scalac.extension.actorServiceKey
import io.scalac.extension.event.ActorEvent
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.util.ReceptionistOps

class AkkaActorAgentTest
    extends ScalaTestWithActorTestKit(classic.ActorSystem("AkkaActorAgentTest").toTyped)
    with AnyFlatSpecLike
    with ReceptionistOps
    with BeforeAndAfterAll {

  import AkkaActorAgentTest._

  override def beforeAll(): Unit = {
    super.beforeAll()
    val instrumentation = ByteBuddyAgent.install()
    val builder =
      new AgentBuilder.Default()
        .`with`(AgentBuilder.Listener.StreamWriting.toSystemOut.withErrorsOnly())
        .`with`(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
        .`with`(AgentBuilder.TypeStrategy.Default.REDEFINE)
        .`with`(AgentBuilder.InitializationStrategy.NoOp.INSTANCE)
    val modules = Map(AkkaActorAgent.moduleName -> AkkaActorAgent.defaultVersion)
    AkkaActorAgent.agent.installOn(builder, instrumentation, modules)
  }

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[ActorEvent]
    Receptionist(system).ref ! Register(actorServiceKey, monitor.ref)
    onlyRef(monitor.ref, actorServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(actorServiceKey, monitor.ref)
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
