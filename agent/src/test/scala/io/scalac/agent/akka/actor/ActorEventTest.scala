package io.scalac.agent.akka.actor

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.{ FishingOutcomes, TestProbe }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorSystem, Behavior }
import io.scalac.agent.utils.{ InstallAgent, SafeLoadSystem }
import io.scalac.core.event.ActorEvent
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.Service.actorService
import io.scalac.core.util.TestBehaviors.ReceptionistPass
import io.scalac.core.util.TestCase.CommonMonitorTestFactory
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ActorEventTest
    extends InstallAgent
    with SafeLoadSystem
    with AnyFlatSpecLike
    with Matchers
    with CommonMonitorTestFactory {

  override type Command = ActorEvent
  private implicit val Timeout                           = scaled(2.seconds)
  override protected val serviceKey: ServiceKey[Command] = actorService.serviceKey

  protected def createMonitorBehavior(implicit context: Context): Behavior[Command] =
    ReceptionistPass(actorService.serviceKey, monitor.ref)

  override type Monitor = TestProbe[ActorEvent]

  override protected def createMonitor(implicit system: ActorSystem[_]): Monitor = createTestProbe

  "ActorAgent" should "publish ActorCreated event" in testCase { implicit context =>
    val id           = createUniqueId
    val ExpectedPath = s"/system/${id}"
    val ref          = system.systemActorOf(Behaviors.ignore, id)

    monitor.fishForMessage(Timeout) {
      case ActorCreated(ref, _) if ref.path.toStringWithoutAddress == ExpectedPath => FishingOutcomes.complete
      case _                                                                       => FishingOutcomes.continueAndIgnore
    }

    ref.unsafeUpcast[Any] ! PoisonPill
  }

}
