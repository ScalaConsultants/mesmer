package io.scalac.agent.akka.persistence

import java.util.UUID

import _root_.akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import _root_.akka.actor.typed.receptionist.Receptionist
import _root_.akka.actor.typed.receptionist.Receptionist.Register
import _root_.akka.util.Timeout
import io.scalac.`extension`.persistenceServiceKey
import io.scalac.agent.utils.DummyEventSourcedActor
import io.scalac.agent.utils.DummyEventSourcedActor.Command
import io.scalac.extension.event.PersistenceEvent
import io.scalac.extension.event.PersistenceEvent.{ RecoveryFinished, RecoveryStarted }
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Minute, Second, Span }
import org.scalatest.{ BeforeAndAfterAll, OptionValues }

import scala.concurrent.duration._
class AkkaPersistenceAgentSpec
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ScalaFutures
    with OptionValues {

  implicit val askTimeout = Timeout(1.minute)
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(1, Minute)), scaled(Span(1, Second)))

  "AkkaPersistenceAgent" should "intercept recovery time and store it in the agent state" in {

    val id      = UUID.randomUUID()
    val monitor = createTestProbe[PersistenceEvent]
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)

    val actor = system.systemActorOf(DummyEventSourcedActor(id), id.toString)

    actor ! Command

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val instrumentation = ByteBuddyAgent.install()

    val builder = new AgentBuilder.Default()

    val modules = Map(AkkaPersistenceAgent.moduleName -> AkkaPersistenceAgent.defaultVersion)

    AkkaPersistenceAgent.agent.installOn(builder, instrumentation, modules)
  }
}
