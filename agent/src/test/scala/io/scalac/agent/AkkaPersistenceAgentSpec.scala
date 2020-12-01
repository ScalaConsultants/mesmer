package io.scalac.agent

import java.util.UUID

import _root_.akka.actor.typed.ActorSystem
import _root_.akka.actor.typed.receptionist.Receptionist
import _root_.akka.actor.typed.scaladsl.AskPattern._
import _root_.akka.actor.typed.scaladsl.Behaviors
import _root_.akka.util.Timeout
import io.scalac.agent.DummyEventsourcedActor.Command
import net.bytebuddy.agent.ByteBuddyAgent
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Minute, Second, Span}
import org.scalatest.{BeforeAndAfterAll, OptionValues}

import scala.concurrent.duration._
import scala.util.Try
class AkkaPersistenceAgentSpec
    extends AnyFlatSpec
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with OptionValues {

  implicit val actorSystem = ActorSystem[Nothing](Behaviors.empty, "AkkaPersistenceAgentSpec")

  implicit val askTimeout = Timeout(1.minute)
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(1, Minute)), scaled(Span(1, Second)))

  Receptionist

  "AkkaPersistenceAgent" should "intercept recovery time and store it in the agent state" in {
    val id    = UUID.randomUUID()
    val actor = actorSystem.systemActorOf(DummyEventsourcedActor(id), id.toString)
    actor.ask(Command).futureValue

    val measurement = Try(AkkaPersistenceAgentState.recoveryMeasurements.get(s"/system/$id")).toOption
    (measurement.value > 0L) shouldBe true
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val agent = ByteBuddyAgent.install()
//    AkkaPersistenceAgent.install(agent)
  }

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
    super.afterAll()
  }
}
