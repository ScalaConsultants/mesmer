package io.scalac.mesmer.instrumentation.akka.peristence

import _root_.akka.actor.testkit.typed.scaladsl.TestProbe
import _root_.akka.actor.typed.receptionist.Receptionist
import _root_.akka.actor.typed.receptionist.Receptionist.{Deregister, Register}
import _root_.akka.util.Timeout
import io.scalac.mesmer.agent.utils.DummyEventSourcedActor._
import io.scalac.mesmer.agent.utils.{DummyEventSourcedActor, InstallModule, SafeLoadSystem}
import io.scalac.mesmer.core.event.PersistenceEvent
import io.scalac.mesmer.core.event.PersistenceEvent._
import io.scalac.mesmer.core.persistenceServiceKey
import io.scalac.mesmer.core.util.ReceptionistOps
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.AkkaPersistenceAgent
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import java.util.UUID
import scala.concurrent.duration._

class AkkaPersistenceAgentTest
    extends InstallModule(AkkaPersistenceAgent)
    with AnyFlatSpecLike
    with Matchers
    with ScalaFutures
    with OptionValues
    with ReceptionistOps
    with SafeLoadSystem {

  implicit val askTimeout: Timeout = Timeout(1.minute)

  type Fixture = TestProbe[PersistenceEvent]

  def test(body: Fixture => Any): Any = {
    val monitor = createTestProbe[PersistenceEvent]
    Receptionist(system).ref ! Register(persistenceServiceKey, monitor.ref)
    onlyRef(monitor.ref, persistenceServiceKey)
    body(monitor)
    Receptionist(system).ref ! Deregister(persistenceServiceKey, monitor.ref)
  }

  "AkkaPersistenceAgent" should "generate only recovery events" in test { monitor =>
    agent.instrumentations should have size (4)
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
