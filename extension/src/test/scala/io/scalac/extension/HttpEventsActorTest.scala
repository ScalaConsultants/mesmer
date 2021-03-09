package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import io.scalac.core.model._
import io.scalac.core.tagging._
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent.{ RequestCompleted, RequestStarted }
import io.scalac.extension.http.MutableRequestStorage
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.metric.HttpMetricMonitor.Labels
import io.scalac.extension.util.TestConfig.localActorProvider
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.probe.HttpMetricsTestProbe
import io.scalac.extension.util.{ IdentityPathService, MonitorFixture, TerminationRegistryOps, TestOps }
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps

class HttpEventsActorTest
    extends ScalaTestWithActorTestKit(localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with Eventually
    with OptionValues
    with Inside
    with BeforeAndAfterAll
    with TerminationRegistryOps
    with LoneElement
    with TestOps
    with MonitorFixture {

  override type Monitor = HttpMetricsTestProbe

  override type Command = HttpEventsActor.Event

  override protected def createMonitor: Monitor = new HttpMetricsTestProbe()

  override protected def setUp(monitor: Monitor, cache: Boolean) =
    system.systemActorOf(
      HttpEventsActor(
        if (cache) CachingMonitor(monitor) else monitor,
        MutableRequestStorage.empty,
        IdentityPathService
      ),
      createUniqueId
    )

  override protected val serviceKey = Some(httpServiceKey)

  def requestStarted(id: String, labels: Labels): Unit = EventBus(system).publishEvent(
    RequestStarted(id, Timestamp.create(), labels.path, labels.method)
  )

  def requestCompleted(id: String): Unit =
    EventBus(system).publishEvent(RequestCompleted(id, Timestamp.create()))

  "HttpEventsActor" should "collect metrics for single request" in test { monitor =>
    val expectedLabels = Labels(None, "/api/v1/test".taggedWith[PathTag], "GET".taggedWith[MethodTag])

    val id = createUniqueId
    requestStarted(id, expectedLabels)
    Thread.sleep(1050)
    requestCompleted(id)
    eventually(monitor.boundSize shouldBe 1)

    monitor.boundLabels should contain theSameElementsAs (Seq(expectedLabels))
    val boundProbes = monitor.probes(expectedLabels)

    boundProbes.value.requestCounterProbe.receiveMessage() should be(Inc(1L))
    inside(boundProbes.value.requestTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value shouldBe 1000L +- 100L
    }
  }

  it should "reuse monitors for same labels" in testCaching { monitor =>
    val expectedLabels = List(
      Labels(None, "/api/v1/test".taggedWith[PathTag], "GET".taggedWith[MethodTag]),
      Labels(None, "/api/v2/test".taggedWith[PathTag], "POST".taggedWith[MethodTag])
    )
    val requestCount = 10

    for {
      label <- expectedLabels
      id    <- List.fill(requestCount)(createUniqueId)
    } {
      requestStarted(id, label)
      requestCompleted(id)
    }

    monitor.globalRequestCounter.receiveMessages(requestCount * expectedLabels.size)

    monitor.binds should be(2)
  }

  it should "collect metric for several concurrent requests" in testCaching { monitor =>
    val labels =
      List.fill(10)(createUniqueId).map(id => Labels(None, id.taggedWith[PathTag], "GET".taggedWith[MethodTag]))
    val requests = labels.map(l => createUniqueId -> l).toMap
    requests.foreach(Function.tupled(requestStarted))
    Thread.sleep(1050)
    requests.keys.foreach(requestCompleted)

    monitor.globalRequestCounter.receiveMessages(requests.size)

    monitor.boundLabels should contain theSameElementsAs (labels)

    val allProbes = monitor.boundLabels.flatMap(monitor.probes)
    allProbes should have size labels.size
    forAll(allProbes) { probes =>
      import probes._
      requestCounterProbe.within(500 millis) {
        requestCounterProbe.receiveMessage() should be(Inc(1L))
        requestCounterProbe.expectNoMessage(requestCounterProbe.remaining)
      }
      inside(requestTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
        value shouldBe 1000L +- 100L
      }
    }
  }

}
