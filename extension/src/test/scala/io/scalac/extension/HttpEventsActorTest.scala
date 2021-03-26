package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.{ ActorSystem, Behavior }

import io.scalac.core.model._
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent.{ ConnectionCompleted, ConnectionStarted, RequestCompleted, RequestStarted }
import io.scalac.extension.http.MutableRequestStorage
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.metric.HttpMetricMonitor
import io.scalac.extension.metric.HttpConnectionMetricMonitor
import io.scalac.extension.util.TestCase.CommonMonitorTestFactory
import io.scalac.extension.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.probe.{ HttpConnectionMetricsTestProbe, HttpMetricsTestProbe }
import io.scalac.extension.util.{ IdentityPathService, TestOps, _ }
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

  type Monitor = _Monitor
  case class _Monitor(request: HttpMetricsTestProbe, connection: HttpConnectionMetricsTestProbe)

  protected val serviceKey: ServiceKey[_] = httpServiceKey

  protected def createMonitorBehavior(implicit context: BasicContext[Monitor]): Behavior[_] = {
    val _Monitor(requestMonitor, connectionMonitor) = monitor
    HttpEventsActor(
      if (context.caching) CachingMonitor(requestMonitor) else requestMonitor,
      if (context.caching) CachingMonitor(connectionMonitor) else connectionMonitor,
      MutableRequestStorage.empty,
      IdentityPathService
    )
  }

  protected def createMonitor(implicit s: ActorSystem[_]): Monitor =
    _Monitor(new HttpMetricsTestProbe()(s), new HttpConnectionMetricsTestProbe()(s))

  def connectionStarted(labels: HttpConnectionMetricMonitor.Labels): Unit =
    EventBus(system).publishEvent(ConnectionStarted(labels.interface, labels.port))

  def connectionCompleted(labels: HttpConnectionMetricMonitor.Labels): Unit =
    EventBus(system).publishEvent(ConnectionCompleted(labels.interface, labels.port))

  def requestStarted(id: String, labels: HttpMetricMonitor.Labels): Unit =
    EventBus(system).publishEvent(RequestStarted(id, Timestamp.create(), labels.path, labels.method))

  def requestCompleted(id: String, status: Status): Unit =
    EventBus(system).publishEvent(RequestCompleted(id, Timestamp.create(), status))

  "HttpEventsActor" should "collect metrics for single request" in testCase { implicit c =>
    val status: Status           = "200"
    val expectedConnectionLabels = HttpConnectionMetricMonitor.Labels(None, "0.0.0.0", 8080)
    val expectedRequestLabels    = HttpMetricMonitor.Labels(None, "/api/v1/test", "GET", status)

    connectionStarted(expectedConnectionLabels)
    eventually(monitor.connection.boundSize shouldBe 1)(patienceConfig, implicitly, implicitly)

    val id = createUniqueId
    requestStarted(id, expectedRequestLabels)
    Thread.sleep(1050)
    requestCompleted(id, status)
    eventually(monitor.request.boundSize shouldBe 1)(patienceConfig, implicitly, implicitly)

    monitor.connection.boundLabels should contain theSameElementsAs Seq(expectedConnectionLabels)
    monitor.request.boundLabels should contain theSameElementsAs Seq(expectedRequestLabels)

    val requestBoundProbes = monitor.request.probes(expectedRequestLabels)
    requestBoundProbes.value.requestCounterProbe.receiveMessage() should be(Inc(1L))
    inside(requestBoundProbes.value.requestTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value shouldBe 1000L +- 100L
    }

    val connectionBoundProbes = monitor.connection.probes(expectedConnectionLabels)
    connectionBoundProbes.value.connectionCounterProbe.receiveMessage() should be(Inc(1L))
    connectionCompleted(expectedConnectionLabels)
    connectionBoundProbes.value.connectionCounterProbe.receiveMessage() should be(Dec(1L))

  }

  it should "reuse monitors for same labels" in testCaseWith(_.withCaching) { implicit c =>
    val expectedConnectionLabels = List(
      HttpConnectionMetricMonitor.Labels(None, "0.0.0.0", 8080),
      HttpConnectionMetricMonitor.Labels(None, "0.0.0.0", 8081)
    )

    val expectedRequestLabels = List(
      HttpMetricMonitor.Labels(None, "/api/v1/test", "GET", "200"),
      HttpMetricMonitor.Labels(None, "/api/v2/test", "POST", "201")
    )
    val requestCount = 10

    expectedConnectionLabels.foreach(connectionStarted)

    for {
      label <- expectedRequestLabels
      id    <- List.fill(requestCount)(createUniqueId)
    } {
      requestStarted(id, label)
      requestCompleted(id, label.status)
    }

    expectedConnectionLabels.foreach(connectionCompleted)

    monitor.connection.globalConnectionCounter.receiveMessages(2 * expectedConnectionLabels.size)
    monitor.request.globalRequestCounter.receiveMessages(requestCount * expectedRequestLabels.size)

    monitor.connection.binds should be(expectedConnectionLabels.size)
    monitor.request.binds should be(expectedRequestLabels.size)
  }

  it should "collect metric for several concurrent requests" in testCaseWith(_.withCaching) { implicit c =>
    val connectionLabels = List.tabulate(10)(i => HttpConnectionMetricMonitor.Labels(None, "0.0.0.0", 8080 + i))
    connectionLabels.foreach(connectionStarted)

    val requestLabels = List.fill(10)(createUniqueId).map(id => HttpMetricMonitor.Labels(None, id, "GET", "204"))
    val requests      = requestLabels.map(l => createUniqueId -> l).toMap
    requests.foreach(Function.tupled(requestStarted))
    Thread.sleep(1050)
    requests.foreach { case (id, labels) =>
      requestCompleted(id, labels.status)
    }

    connectionLabels.foreach(connectionCompleted)

    monitor.connection.globalConnectionCounter.receiveMessages(connectionLabels.size)
    monitor.request.globalRequestCounter.receiveMessages(requests.size)

    monitor.connection.boundLabels should contain theSameElementsAs connectionLabels
    monitor.request.boundLabels should contain theSameElementsAs requestLabels

    val connectionProbes = connectionLabels.flatMap(monitor.connection.probes)
    connectionProbes should have size connectionLabels.size
    forAll(connectionProbes) { probes =>
      import probes._
      connectionCounterProbe.within(500 milliseconds) {
        connectionCounterProbe.receiveMessage() should (be(Dec(1L)) or be(Inc(1L)))
      }
    }

    val requestProbes = requestLabels.flatMap(monitor.request.probes)
    requestProbes should have size requestLabels.size
    forAll(requestProbes) { probes =>
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
