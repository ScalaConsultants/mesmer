package io.scalac.mesmer.extension
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.ServiceKey
import org.scalatest._
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.existentials
import scala.language.postfixOps

import io.scalac.mesmer.core._
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent._
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.TestCase.CommonMonitorTestFactory
import io.scalac.mesmer.core.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.mesmer.core.util.TestConfig
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.metric.CachingMonitor
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor.Attributes
import io.scalac.mesmer.extension.persistence.ImmutablePersistStorage
import io.scalac.mesmer.extension.persistence.ImmutableRecoveryStorage
import io.scalac.mesmer.extension.util.IdentityPathService
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.Inc
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricRecorded
import io.scalac.mesmer.extension.util.probe.PersistenceMonitorTestProbe

class PersistenceEventsActorTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with CommonMonitorTestFactory {

  type Monitor = PersistenceMonitorTestProbe
  type Command = PersistenceEventsActor.Event

  protected val serviceKey: ServiceKey[_] = persistenceServiceKey

  protected def createMonitorBehavior(implicit context: BasicContext[PersistenceMonitorTestProbe]): Behavior[Command] =
    PersistenceEventsActor(
      if (context.caching) CachingMonitor(monitor) else monitor,
      ImmutableRecoveryStorage.empty,
      ImmutablePersistStorage.empty,
      IdentityPathService
    )

  protected def createMonitor(implicit system: ActorSystem[_]): PersistenceMonitorTestProbe =
    new PersistenceMonitorTestProbe()

  def recoveryStarted(attributes: Attributes)(implicit ctx: BasicContext[PersistenceMonitorTestProbe]): Unit =
    EventBus(system).publishEvent(RecoveryStarted(attributes.path, attributes.persistenceId, Timestamp.create()))

  def recoveryFinished(attributes: Attributes)(implicit ctx: BasicContext[PersistenceMonitorTestProbe]): Unit =
    EventBus(system).publishEvent(RecoveryFinished(attributes.path, attributes.persistenceId, Timestamp.create()))

  def persistEventStarted(seqNo: Long, attributes: Attributes)(implicit
    ctx: BasicContext[PersistenceMonitorTestProbe]
  ): Unit =
    EventBus(system).publishEvent(
      PersistingEventStarted(attributes.path, attributes.persistenceId, seqNo, Timestamp.create())
    )

  def persistEventFinished(seqNo: Long, attributes: Attributes)(implicit
    ctx: BasicContext[PersistenceMonitorTestProbe]
  ): Unit =
    EventBus(system).publishEvent(
      PersistingEventFinished(attributes.path, attributes.persistenceId, seqNo, Timestamp.create())
    )

  def snapshotCreated(seqNo: Long, attributes: Attributes)(implicit
    ctx: BasicContext[PersistenceMonitorTestProbe]
  ): Unit =
    EventBus(system).publishEvent(SnapshotCreated(attributes.path, attributes.persistenceId, seqNo, Timestamp.create()))

  def expectMetricsUpdates(monitor: PersistenceMonitorTestProbe, amount: Int): Unit =
    monitor.globalCounter.within(1 second) {
      import monitor._
      globalCounter.receiveMessages(amount)
      globalCounter.expectNoMessage(globalCounter.remaining)
    }

  "PersistenceEventsActor" should "capture recovery time" in testCase { implicit c =>
    val expectedAttributes = Attributes(None, "/some/path", createUniqueId)
    recoveryStarted(expectedAttributes)
    Thread.sleep(1050)
    recoveryFinished(expectedAttributes)
    expectMetricsUpdates(monitor, 1)
    monitor.boundAttributes should have size 1
    val probes = monitor.boundAttributes.flatMap(monitor.probes).loneElement
    probes.recoveryTotalProbe.receiveMessage() should be(Inc(1L))
    inside(probes.recoveryTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value should be(1000L +- 100)
    }
  }

  it should "capture persist event time" in testCase { implicit c =>
    val seqNo              = 100L
    val expectedAttributes = Attributes(None, "/some/path", createUniqueId)
    persistEventStarted(seqNo, expectedAttributes)
    Thread.sleep(1050)
    persistEventFinished(seqNo, expectedAttributes)
    expectMetricsUpdates(monitor, 1)
    monitor.boundAttributes should have size 1
    val probes = monitor.boundAttributes.flatMap(monitor.probes).loneElement
    probes.persistentEventTotalProbe.receiveMessage() should be(Inc(1L))
    inside(probes.persistentEventProbe.receiveMessage()) { case MetricRecorded(value) =>
      value should be(1000L +- 100)
    }
  }

  it should "capture amount of snapshots for same entity with same monitor" in testCaseWith(_.withCaching) {
    implicit c =>
      val seqNumbers         = (100 to 140 by 5).toList
      val expectedAttributes = Attributes(None, "/some/path", createUniqueId)
      for {
        seqNo <- seqNumbers
      } snapshotCreated(seqNo, expectedAttributes)

      expectMetricsUpdates(monitor, seqNumbers.size)
      monitor.boundAttributes should have size 1
      monitor.binds should be(1)

      val probes = monitor.boundAttributes.flatMap(monitor.probes).loneElement
      forAll(probes.snapshotProbe.receiveMessages(seqNumbers.size))(_ should be(Inc(1L)))
  }

  it should "capture amount of snapshots for same different entities with reused monitors" in testCaseWith(
    _.withCaching
  ) { implicit c =>
    val seqNumbers = (100 to 140 by 5).toList
    val expectedAttributes = List.fill(5) {
      val id = createUniqueId
      Attributes(None, s"/some/path/$id", id)
    }
    for {
      seqNo      <- seqNumbers
      attributes <- expectedAttributes
    } snapshotCreated(seqNo, attributes)

    expectMetricsUpdates(monitor, seqNumbers.size * expectedAttributes.size)
    monitor.boundAttributes should have size expectedAttributes.size
    monitor.binds should be(expectedAttributes.size)

    val allProbes = monitor.boundAttributes.flatMap(monitor.probes)
    allProbes should have size expectedAttributes.size
    forAll(allProbes)(probes => forAll(probes.snapshotProbe.receiveMessages(seqNumbers.size))(_ should be(Inc(1L))))
  }

  it should "capture persist event time with resued monitors for many events" in testCaseWith(_.withCaching) {
    implicit c =>
      val seqNo = 150
      val expectedAttributes = List.fill(5) {
        val id = createUniqueId
        Attributes(None, s"/some/path/$id", id)
      }
      for {
        attributes <- expectedAttributes
      } persistEventStarted(seqNo, attributes)
      Thread.sleep(1050)
      for {
        attributes <- expectedAttributes
      } persistEventFinished(seqNo, attributes)

      expectMetricsUpdates(monitor, expectedAttributes.size)
      monitor.boundAttributes should have size expectedAttributes.size
      monitor.binds should be(expectedAttributes.size)

      val allProbes = monitor.boundAttributes.flatMap(monitor.probes)
      allProbes should have size expectedAttributes.size
      forAll(allProbes) { probes =>
        probes.persistentEventTotalProbe.receiveMessage() should be(Inc(1L))
        inside(probes.persistentEventProbe.receiveMessage()) { case MetricRecorded(value) =>
          value should be(1000L +- 100L)
        }
      }
  }

  it should "capture all metrics persist metrics with reused monitors" in testCaseWith(_.withCaching) { implicit c =>
    val seqNbs                   = List(150, 151, 152)
    val expectedRecoveryTime     = 1000L
    val expectedPersistEventTime = 500L
    val expectedAttributes = List.fill(5) {
      val id = createUniqueId
      Attributes(None, s"/some/path/$id", id)
    }
    expectedAttributes.foreach(recoveryStarted)
    Thread.sleep(expectedRecoveryTime + 50L)
    expectedAttributes.foreach(recoveryFinished)

    seqNbs.foreach { seqNo =>
      for {
        attributes <- expectedAttributes
      } persistEventStarted(seqNo, attributes)

      Thread.sleep(expectedPersistEventTime + 50L)
      for {
        attributes <- expectedAttributes
      } {
        snapshotCreated(seqNo, attributes)
        persistEventFinished(seqNo, attributes)
      }
    }

    expectMetricsUpdates(monitor, expectedAttributes.size * (1 + seqNbs.size * 2))
    monitor.boundAttributes should have size expectedAttributes.size
    monitor.binds should be(expectedAttributes.size)

    val allProbes = monitor.boundAttributes.flatMap(monitor.probes)
    allProbes should have size expectedAttributes.size
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
