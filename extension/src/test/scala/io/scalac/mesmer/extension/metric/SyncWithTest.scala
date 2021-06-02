package io.scalac.mesmer.extension.metric

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.util.TestConfig
import io.scalac.mesmer.core.util.probe.ObserverCollector.ManualCollectorImpl
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricObserved
import io.scalac.mesmer.extension.util.probe.BoundTestProbe.MetricObserverCommand
import io.scalac.mesmer.extension.util.probe.ObserverTestProbeWrapper

class SyncWithTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors {

  "Sync" should "execute once" in {
    val firstProbe  = createTestProbe[MetricObserverCommand[String]]()
    val secondProbe = createTestProbe[MetricObserverCommand[String]]()

    val collector = new ManualCollectorImpl

    val firstObserver  = ObserverTestProbeWrapper(firstProbe, collector)
    val secondObserver = ObserverTestProbeWrapper(secondProbe, collector)

    @volatile
    var counter = 0

    val afterAll = SyncWith()
      .`with`(firstObserver)(updater => updater.observe(111L, "first"))
      .`with`(secondObserver)(updater => updater.observe(222L, "second"))
      .afterAll {
        counter += 1
      }

    collector.collectAll()

    counter should be(1L)
    firstProbe.receiveMessage() should be(MetricObserved(111L, "first"))
    secondProbe.receiveMessage() should be(MetricObserved(222L, "second"))
    afterAll.counter.get() should be(0L)

  }

  it should "execute many" in {
    val firstProbe  = createTestProbe[MetricObserverCommand[String]]()
    val secondProbe = createTestProbe[MetricObserverCommand[String]]()

    val collector = new ManualCollectorImpl

    val firstObserver  = ObserverTestProbeWrapper(firstProbe, collector)
    val secondObserver = ObserverTestProbeWrapper(secondProbe, collector)

    @volatile
    var counter = 0

    val afterAll = SyncWith()
      .`with`(firstObserver)(updater => updater.observe(111L, "first"))
      .`with`(secondObserver)(updater => updater.observe(222L, "second"))
      .afterAll {
        counter += 1
      }

    val messages = 10

    for {
      _ <- 0 until messages
    } collector.collectAll()

    counter should be(10)

    forAll(firstProbe.receiveMessages(10))(_ should be(MetricObserved(111L, "first")))
    forAll(secondProbe.receiveMessages(10))(_ should be(MetricObserved(222L, "second")))
    afterAll.counter.get() should be(0L)

  }

  it should "work with different observer types" in {
    val firstProbe  = createTestProbe[MetricObserverCommand[String]]()
    val secondProbe = createTestProbe[MetricObserverCommand[Int]]()
    val thirdProbe  = createTestProbe[MetricObserverCommand[Boolean]]()
    val forthProbe  = createTestProbe[MetricObserverCommand[(String, String)]]()

    val collector = new ManualCollectorImpl

    val firstObserver  = ObserverTestProbeWrapper(firstProbe, collector)
    val secondObserver = ObserverTestProbeWrapper(secondProbe, collector)
    val thirdObserver  = ObserverTestProbeWrapper(thirdProbe, collector)
    val forthObserver  = ObserverTestProbeWrapper(forthProbe, collector)

    @volatile
    var counter: Int = 0

    val afterAll = SyncWith()
      .`with`(firstObserver)(updater => updater.observe(111L, "first"))
      .`with`(secondObserver)(updater => updater.observe(222L, 1001))
      .`with`(thirdObserver)(updater => updater.observe(333L, false))
      .`with`(forthObserver)(updater => updater.observe(444L, ("fo", "rth")))
      .afterAll {
        counter += 1
      }

    collector.collectAll()

    counter should be(1)
    firstProbe.receiveMessage() should be(MetricObserved(111L, "first"))
    secondProbe.receiveMessage() should be(MetricObserved(222L, 1001))
    thirdProbe.receiveMessage() should be(MetricObserved(333L, false))
    forthProbe.receiveMessage() should be(MetricObserved(444L, ("fo", "rth")))
    afterAll.counter.get() should be(0L)

  }
}
