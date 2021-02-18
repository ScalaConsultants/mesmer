package io.scalac.agent.akka.persistence

import java.util.UUID

import scala.concurrent.duration._

import _root_.akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import _root_.akka.actor.typed.receptionist.Receptionist
import _root_.akka.actor.typed.receptionist.Receptionist.{ Deregister, Register }
import _root_.akka.util.Timeout

import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Minute, Second, Span }

import io.scalac.agent.utils.DummyEventSourcedActor.{ DoNothing, Persist }
import io.scalac.agent.utils.{ AgentLoaderOps, DummyEventSourcedActor }
import io.scalac.extension.event.PersistenceEvent
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.persistenceServiceKey
import io.scalac.extension.util.ReceptionistOps

class AkkaPersistenceAgentSpec
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with OptionValues
    with ReceptionistOps
    with AgentLoaderOps {

  implicit val askTimeout = Timeout(1.minute)
  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(1, Minute)), scaled(Span(1, Second)))

  type Fixture = TestProbe[PersistenceEvent]

  def loadAgent(): Unit = {
    val instrumentation = ByteBuddyAgent.install()
    val builder         = new AgentBuilder.Default()
    val modules         = Map(AkkaPersistenceAgent.moduleName -> AkkaPersistenceAgent.defaultVersion)
    AkkaPersistenceAgent.agent.installOn(builder, instrumentation, modules)
  }

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[PersistenceEvent]
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)
    onlyRef(monitor.ref, persistenceServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(persistenceServiceKey, monitor.ref)
  }

  "AkkaPersistenceAgent" should "generate only recovery events" in test { monitor =>
    val id = UUID.randomUUID()
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)

    val actor = system.systemActorOf(DummyEventSourcedActor(id), id.toString)

    actor ! DoNothing

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
    monitor.expectNoMessage()
  }

  it should "generate recovery, persisting and snapshot events for single persist event" in test { monitor =>
    val id = UUID.randomUUID()
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)

    val actor = system.systemActorOf(DummyEventSourcedActor(id), id.toString)

    actor ! Persist

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
    monitor.expectMessageType[PersistingEventStarted]
    monitor.expectMessageType[PersistingEventFinished]
    monitor.expectMessageType[SnapshotCreated]
  }

  it should "generate recovery, persisting and snapshot events for multiple persist events" in test { monitor =>
    val id = UUID.randomUUID()
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)
    val persistEvents = List.fill(5)(Persist)

    val actor = system.systemActorOf(DummyEventSourcedActor(id, 2), id.toString)

    persistEvents.foreach(actor.tell)

    monitor.expectMessageType[RecoveryStarted]
    monitor.expectMessageType[RecoveryFinished]
    for {
      seqNo <- persistEvents.indices
    } {
      monitor.expectMessageType[PersistingEventStarted]
      monitor.expectMessageType[PersistingEventFinished]
      if (seqNo % 2 == 1) {
        monitor.expectMessageType[SnapshotCreated]
      }
    }
  }

}
