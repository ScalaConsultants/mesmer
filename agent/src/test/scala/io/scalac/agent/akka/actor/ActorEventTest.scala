package io.scalac.agent.akka.actor

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.{ FishingOutcomes, TestProbe }
import akka.actor.typed.{ ActorSystem, Behavior, SupervisorStrategy }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import io.scalac.agent.utils.{ InstallAgent, SafeLoadSystem }
import io.scalac.core.event.ActorEvent
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.Service.actorService
import io.scalac.core.model.ActorRefDetails
import io.scalac.core.util.TestBehaviors
import io.scalac.core.util.TestBehaviors.Pass
import io.scalac.core.util.TestCase.CommonMonitorTestFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span

import scala.concurrent.duration._

class ActorEventTest
    extends InstallAgent
    with SafeLoadSystem
    with AnyFlatSpecLike
    with Matchers
    with CommonMonitorTestFactory {

  type Command = ActorEvent
  type Monitor = TestProbe[ActorEvent]
  private implicit val Timeout: Span                     = scaled(2.seconds)
  override protected val serviceKey: ServiceKey[Command] = actorService.serviceKey

  protected def createMonitorBehavior(implicit context: Context): Behavior[Command] =
    Pass.registerService(actorService.serviceKey, monitor.ref)

  protected def createMonitor(implicit system: ActorSystem[_]): Monitor = createTestProbe

  "ActorAgent" should "publish ActorCreated event" in testCase { implicit context =>
    val id          = createUniqueId
    val ref         = system.systemActorOf(Behaviors.ignore, id)
    val expectedRef = ref.toClassic

    monitor.fishForMessage(Timeout) {
      case ActorCreated(ActorRefDetails(`expectedRef`, _)) => FishingOutcomes.complete
      case _                                               => FishingOutcomes.continueAndIgnore
    }

    //cleanup
    ref.unsafeUpcast[Any] ! PoisonPill
  }

  it should "not receive actor created message when typed actor restarts" in testCase { implicit context =>
    val id = createUniqueId
    val ref =
      system.systemActorOf(
        Behaviors
          .supervise(TestBehaviors.Failing[Any]())
          .onFailure(SupervisorStrategy.restart),
        id
      )

    val expectedRef = ref.toClassic

    monitor.fishForMessage(Timeout) {
      case ActorCreated(ActorRefDetails(`expectedRef`, _)) => FishingOutcomes.complete
      case _                                               => FishingOutcomes.continueAndIgnore
    }

    ref ! () // this will trigger restart

    monitor.expectNoMessage(Timeout)

    //cleanup
    ref.unsafeUpcast[Any] ! PoisonPill
  }

  it should "not receive actor created message when classic actor restarts" in testCase { implicit context =>
    val id  = createUniqueId
    val ref = classicSystem.systemActorOf(TestBehaviors.Failing.classic, id)

    monitor.fishForMessage(Timeout) {
      case ActorCreated(ActorRefDetails(`ref`, _)) => FishingOutcomes.complete
      case _                                       => FishingOutcomes.continueAndIgnore
    }

    ref ! () // this will init restart

    monitor.expectNoMessage()

  }

}
