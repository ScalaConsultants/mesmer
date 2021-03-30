package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ ActorSystem, Behavior }
import io.scalac.core._
import io.scalac.core.model._
import io.scalac.core.util.Timestamp
import io.scalac.core.event.EventBus
import io.scalac.core.event.HttpEvent.{ RequestCompleted, RequestStarted }
import io.scalac.extension.http.MutableRequestStorage
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.metric.HttpMetricMonitor.Labels
import io.scalac.core.util.TestCase.CommonMonitorTestFactory
import io.scalac.core.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.core.util.probe.BoundTestProbe._
import io.scalac.core.util.probe.HttpMetricsTestProbe
import io.scalac.core.util.{ IdentityPathService, TestOps, _ }
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ Status => _, _ }

import scala.concurrent.duration._
import scala.language.postfixOps

class HttpEventsActorTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with Eventually
    with OptionValues
    with Inside
    with BeforeAndAfterAll
    with LoneElement
    with TestOps
    with CommonMonitorTestFactory {

  type Monitor = HttpMetricsTestProbe

  protected val serviceKey: ServiceKey[_] = httpServiceKey

  protected def createMonitorBehavior(implicit context: BasicContext[HttpMetricsTestProbe]): Behavior[_] =
    HttpEventsActor(
      if (context.caching) CachingMonitor(monitor) else monitor,
      MutableRequestStorage.empty,
      IdentityPathService
    )

  protected def createMonitor(implicit s: ActorSystem[_]): HttpMetricsTestProbe = new HttpMetricsTestProbe()(s)

  def requestStarted(id: String, labels: Labels): Unit =
    EventBus(system).publishEvent(RequestStarted(id, Timestamp.create(), labels.path, labels.method))

  def requestCompleted(id: String, status: Status): Unit =
    EventBus(system).publishEvent(RequestCompleted(id, Timestamp.create(), status))

  "HttpEventsActor" should "collect metrics for single request" in testCase { implicit c =>
    val status: Status = "200"
    val expectedLabels = Labels(None, "/api/v1/test", "GET", status)

    val id = createUniqueId
    requestStarted(id, expectedLabels)
    Thread.sleep(1050)
    requestCompleted(id, status)
    eventually(monitor.boundSize shouldBe 1)(patienceConfig, implicitly, implicitly)

    monitor.boundLabels should contain theSameElementsAs (Seq(expectedLabels))
    val boundProbes = monitor.probes(expectedLabels)

    boundProbes.value.requestCounterProbe.receiveMessage() should be(Inc(1L))
    inside(boundProbes.value.requestTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value shouldBe 1000L +- 100L
    }
  }

  it should "reuse monitors for same labels" in testCaseWith(_.withCaching) { implicit c =>
    val expectedLabels = List(Labels(None, "/api/v1/test", "GET", "200"), Labels(None, "/api/v2/test", "POST", "201"))
    val requestCount   = 10

    for {
      label <- expectedLabels
      id    <- List.fill(requestCount)(createUniqueId)
    } {
      requestStarted(id, label)
      requestCompleted(id, label.status)
    }

    monitor.globalRequestCounter.receiveMessages(requestCount * expectedLabels.size)

    monitor.binds should be(2)
  }

  it should "collect metric for several concurrent requests" in testCaseWith(_.withCaching) { implicit c =>
    val labels   = List.fill(10)(createUniqueId).map(id => Labels(None, id, "GET", "204"))
    val requests = labels.map(l => createUniqueId -> l).toMap
    requests.foreach(Function.tupled(requestStarted))
    Thread.sleep(1050)
    requests.foreach { case (id, labels) =>
      requestCompleted(id, labels.status)
    }

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
