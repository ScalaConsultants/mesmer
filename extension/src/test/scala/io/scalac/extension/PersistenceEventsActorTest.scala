package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.persistence.{ InMemoryPersistStorage, InMemoryRecoveryStorage }
import io.scalac.extension.util.probe.BoundTestProbe.{ Inc, MetricRecorded }
import io.scalac.extension.util.probe.PersistenceMetricTestProbe
import io.scalac.extension.util.{ IdentityPathService, MonitorFixture }
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps

class PersistenceEventsActorTest
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorFixture {
  override type Monitor = PersistenceMetricTestProbe

  override protected def createMonitor: Monitor = new PersistenceMetricTestProbe()

  override protected def setUp(monitor: Monitor): ActorRef[_] =
    system.systemActorOf(
      PersistenceEventsActor(IdentityPathService, InMemoryRecoveryStorage.empty, InMemoryPersistStorage.empty, monitor),
      createUniqueId
    )

  def recoveryStarted(labels: Labels): Unit =
    EventBus(system).publishEvent(RecoveryStarted(labels.path, labels.persistenceId, System.currentTimeMillis()))

  def recoveryFinished(labels: Labels): Unit =
    EventBus(system).publishEvent(RecoveryFinished(labels.path, labels.persistenceId, System.currentTimeMillis()))

  def persistEventStarted(seqNo: Long, labels: Labels): Unit =
    EventBus(system).publishEvent(
      PersistingEventStarted(labels.path, labels.persistenceId, seqNo, System.currentTimeMillis())
    )

  def persistEventFinished(seqNo: Long, labels: Labels): Unit =
    EventBus(system).publishEvent(
      PersistingEventFinished(labels.path, labels.persistenceId, seqNo, System.currentTimeMillis())
    )

  def snapshotCreated(seqNo: Long, labels: Labels): Unit =
    EventBus(system).publishEvent(SnapshotCreated(labels.path, labels.persistenceId, seqNo, System.currentTimeMillis()))

  def expectMetricsUpdates(monitor: Monitor, amount: Int): Unit =
    monitor.globalCounter.within(1 second) {
      import monitor._
      globalCounter.receiveMessages(amount)
      globalCounter.expectNoMessage(globalCounter.remaining)
    }

  override protected val serviceKey: ServiceKey[_] = persistenceServiceKey

  "PersistenceEventsActor" should "capture recovery time" in test { monitor =>
    val expectedLabels = Labels(None, "/some/path", createUniqueId)
    recoveryStarted(expectedLabels)
    Thread.sleep(1050)
    recoveryFinished(expectedLabels)
    expectMetricsUpdates(monitor, 1)
    monitor.boundLabels should have size (1)
    val probes = monitor.boundLabels.flatMap(monitor.probes).loneElement
    probes.recoveryTotalProbe.receiveMessage() should be(Inc(1L))
    inside(probes.recoveryTimeProbe.receiveMessage()) {
      case MetricRecorded(value) => value should be(1000L +- 100)
    }
  }

  it should "capture persist event time" in test { monitor =>
    val seqNo          = 100L
    val expectedLabels = Labels(None, "/some/path", createUniqueId)
    persistEventStarted(seqNo, expectedLabels)
    Thread.sleep(1050)
    persistEventFinished(seqNo, expectedLabels)
    expectMetricsUpdates(monitor, 1)
    monitor.boundLabels should have size (1)
    val probes = monitor.boundLabels.flatMap(monitor.probes).loneElement
    probes.persistentEventTotalProbe.receiveMessage() should be(Inc(1L))
    inside(probes.persistentEventProbe.receiveMessage()) {
      case MetricRecorded(value) => value should be(1000L +- 100)
    }
  }

  it should "capture amount of snapshots for same entity with same monitor" in test { monitor =>
    val seqNumbers     = (100 to 140 by 5).toList
    val expectedLabels = Labels(None, "/some/path", createUniqueId)
    for {
      seqNo <- seqNumbers
    } snapshotCreated(seqNo, expectedLabels)

    expectMetricsUpdates(monitor, seqNumbers.size)
    monitor.boundLabels should have size (1)
    monitor.binds should be(1)

    val probes = monitor.boundLabels.flatMap(monitor.probes).loneElement
    forAll(probes.snapshotProbe.receiveMessages(seqNumbers.size))(_ should be(Inc(1L)))
  }

  it should "capture amount of snapshots for same different entities with reused monitors" in test { monitor =>
    val seqNumbers = (100 to 140 by 5).toList
    val expectedLabels = List.fill(5) {
      val id = createUniqueId
      Labels(None, s"/some/path/${id}", id)
    }
    for {
      seqNo  <- seqNumbers
      labels <- expectedLabels
    } snapshotCreated(seqNo, labels)

    expectMetricsUpdates(monitor, seqNumbers.size * expectedLabels.size)
    monitor.boundLabels should have size (expectedLabels.size)
    monitor.binds should be(expectedLabels.size)

    val allProbes = monitor.boundLabels.flatMap(monitor.probes)
    allProbes should have size (expectedLabels.size)
    forAll(allProbes)(probes => forAll(probes.snapshotProbe.receiveMessages(seqNumbers.size))(_ should be(Inc(1L))))
  }

  it should "capture persist event time with resued monitors" in test { monitor =>
    val seqNo = 150
    val expectedLabels = List.fill(5) {
      val id = createUniqueId
      Labels(None, s"/some/path/${id}", id)
    }
    for {
      labels <- expectedLabels
    } persistEventStarted(seqNo, labels)
    Thread.sleep(1050)
    for {
      labels <- expectedLabels
    } persistEventFinished(seqNo, labels)

    expectMetricsUpdates(monitor, expectedLabels.size)
    monitor.boundLabels should have size (expectedLabels.size)
    monitor.binds should be(expectedLabels.size)

    val allProbes = monitor.boundLabels.flatMap(monitor.probes)
    allProbes should have size (expectedLabels.size)
    forAll(allProbes) { probes =>
      probes.persistentEventTotalProbe.receiveMessage() should be(Inc(1L))
      inside(probes.persistentEventProbe.receiveMessage()) {
        case MetricRecorded(value) => value should be(1000L +- 100L)
      }
    }
  }
}
