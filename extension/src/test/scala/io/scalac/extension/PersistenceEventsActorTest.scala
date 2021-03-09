package io.scalac.extension

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ ActorSystem, Behavior }

import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.persistence.{ ImmutablePersistStorage, ImmutableRecoveryStorage }
import io.scalac.extension.util.{ IdentityPathService, TestConfig }
import io.scalac.extension.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.extension.util.TestCase.CommonMonitorTestFactory
import io.scalac.extension.util.probe.BoundTestProbe.{ Inc, MetricRecorded }
import io.scalac.extension.util.probe.PersistenceMetricTestProbe

class PersistenceEventsActorTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with CommonMonitorTestFactory {

  type Monitor = PersistenceMetricTestProbe

  protected val serviceKey: ServiceKey[_] = persistenceServiceKey

  protected def createMonitorBehavior(implicit context: BasicContext[PersistenceMetricTestProbe]): Behavior[_] =
    PersistenceEventsActor(
      if (context.caching) CachingMonitor(monitor) else monitor,
      ImmutableRecoveryStorage.empty,
      ImmutablePersistStorage.empty,
      IdentityPathService
    )

  protected def createMonitor(implicit system: ActorSystem[_]): PersistenceMetricTestProbe =
    new PersistenceMetricTestProbe()

  def recoveryStarted(labels: Labels)(implicit ctx: BasicContext[PersistenceMetricTestProbe]): Unit =
    EventBus(system).publishEvent(RecoveryStarted(labels.path, labels.persistenceId, Timestamp.create()))

  def recoveryFinished(labels: Labels)(implicit ctx: BasicContext[PersistenceMetricTestProbe]): Unit =
    EventBus(system).publishEvent(RecoveryFinished(labels.path, labels.persistenceId, Timestamp.create()))

  def persistEventStarted(seqNo: Long, labels: Labels)(implicit ctx: BasicContext[PersistenceMetricTestProbe]): Unit =
    EventBus(system).publishEvent(
      PersistingEventStarted(labels.path, labels.persistenceId, seqNo, Timestamp.create())
    )

  def persistEventFinished(seqNo: Long, labels: Labels)(implicit ctx: BasicContext[PersistenceMetricTestProbe]): Unit =
    EventBus(system).publishEvent(
      PersistingEventFinished(labels.path, labels.persistenceId, seqNo, Timestamp.create())
    )

  def snapshotCreated(seqNo: Long, labels: Labels)(implicit ctx: BasicContext[PersistenceMetricTestProbe]): Unit =
    EventBus(system).publishEvent(SnapshotCreated(labels.path, labels.persistenceId, seqNo, Timestamp.create()))

  def expectMetricsUpdates(monitor: PersistenceMetricTestProbe, amount: Int): Unit =
    monitor.globalCounter.within(1 second) {
      import monitor._
      globalCounter.receiveMessages(amount)
      globalCounter.expectNoMessage(globalCounter.remaining)
    }

  "PersistenceEventsActor" should "capture recovery time" in testCase { implicit c =>
    val expectedLabels = Labels(None, "/some/path", createUniqueId)
    recoveryStarted(expectedLabels)
    Thread.sleep(1050)
    recoveryFinished(expectedLabels)
    expectMetricsUpdates(monitor, 1)
    monitor.boundLabels should have size (1)
    val probes = monitor.boundLabels.flatMap(monitor.probes).loneElement
    probes.recoveryTotalProbe.receiveMessage() should be(Inc(1L))
    inside(probes.recoveryTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value should be(1000L +- 100)
    }
  }

  it should "capture persist event time" in testCase { implicit c =>
    val seqNo          = 100L
    val expectedLabels = Labels(None, "/some/path", createUniqueId)
    persistEventStarted(seqNo, expectedLabels)
    Thread.sleep(1050)
    persistEventFinished(seqNo, expectedLabels)
    expectMetricsUpdates(monitor, 1)
    monitor.boundLabels should have size (1)
    val probes = monitor.boundLabels.flatMap(monitor.probes).loneElement
    probes.persistentEventTotalProbe.receiveMessage() should be(Inc(1L))
    inside(probes.persistentEventProbe.receiveMessage()) { case MetricRecorded(value) =>
      value should be(1000L +- 100)
    }
  }

  it should "capture amount of snapshots for same entity with same monitor" in testCaseWith(_.withCaching) {
    implicit c =>
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

  it should "capture amount of snapshots for same different entities with reused monitors" in testCaseWith(
    _.withCaching
  ) { implicit c =>
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

  it should "capture persist event time with resued monitors for many events" in testCaseWith(_.withCaching) {
    implicit c =>
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

  it should "capture all metrics persist metrics with reused monitors" in testCaseWith(_.withCaching) { implicit c =>
    val seqNbs                   = List(150, 151, 152)
    val expectedRecoveryTime     = 1000L
    val expectedPersistEventTime = 500L
    val expectedLabels = List.fill(5) {
      val id = createUniqueId
      Labels(None, s"/some/path/${id}", id)
    }
    expectedLabels.foreach(recoveryStarted)
    Thread.sleep(expectedRecoveryTime + 50L)
    expectedLabels.foreach(recoveryFinished)

    seqNbs.foreach { seqNo =>
      for {
        labels <- expectedLabels
      } persistEventStarted(seqNo, labels)

      Thread.sleep(expectedPersistEventTime + 50L)
      for {
        labels <- expectedLabels
      } {
        snapshotCreated(seqNo, labels)
        persistEventFinished(seqNo, labels)
      }
    }

    expectMetricsUpdates(monitor, expectedLabels.size * (1 + seqNbs.size * 2))
    monitor.boundLabels should have size (expectedLabels.size)
    monitor.binds should be(expectedLabels.size)

    val allProbes = monitor.boundLabels.flatMap(monitor.probes)
    allProbes should have size (expectedLabels.size)
    forAll(allProbes) { probes =>
      forAll(probes.persistentEventTotalProbe.receiveMessages(seqNbs.size))(_ should be(Inc(1L)))
      forAll(probes.persistentEventProbe.receiveMessages(seqNbs.size))(mr =>
        inside(mr) { case MetricRecorded(value) =>
          value should be(expectedPersistEventTime +- 100L)
        }
      )
      forAll(probes.snapshotProbe.receiveMessages(seqNbs.size))(_ should be(Inc(1L)))

      probes.recoveryTotalProbe.receiveMessage() should be(Inc(1L))
      inside(probes.recoveryTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
        value should be(expectedRecoveryTime +- 100L)
      }
    }
  }
}
