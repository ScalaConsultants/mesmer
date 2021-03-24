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
import io.scalac.extension.metric.HttpMetricMonitor.{ ConnectionLabels, RequestLabels }
import io.scalac.extension.util.TestCase.CommonMonitorTestFactory
import io.scalac.extension.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.probe.HttpMetricsTestProbe
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

  type Monitor = HttpMetricsTestProbe

  protected val serviceKey: ServiceKey[_] = httpServiceKey

  protected def createMonitorBehavior(implicit context: BasicContext[HttpMetricsTestProbe]): Behavior[_] =
    HttpEventsActor(
      if (context.caching) CachingMonitor(monitor) else monitor,
      MutableRequestStorage.empty,
      IdentityPathService
    )

  protected def createMonitor(implicit s: ActorSystem[_]): HttpMetricsTestProbe = new HttpMetricsTestProbe()(s)

  def connectionStarted(labels: ConnectionLabels): Unit =
    EventBus(system).publishEvent(ConnectionStarted(labels.interface, labels.port))

  def connectionCompleted(labels: ConnectionLabels): Unit =
    EventBus(system).publishEvent(ConnectionCompleted(labels.interface, labels.port))

  def requestStarted(id: String, labels: RequestLabels): Unit =
    EventBus(system).publishEvent(RequestStarted(id, Timestamp.create(), labels.path, labels.method))

  def requestCompleted(id: String, status: Status): Unit =
    EventBus(system).publishEvent(RequestCompleted(id, Timestamp.create(), status))

  "HttpEventsActor" should "collect metrics for single request" in testCase { implicit c =>
    val status: Status           = "200"
    val expectedConnectionLabels = ConnectionLabels(None, "0.0.0.0", 8080)
    val expectedRequestLabels    = RequestLabels(None, "/api/v1/test", "GET", status)

    connectionStarted(expectedConnectionLabels)
    eventually(monitor.boundSize shouldBe 1)(patienceConfig, implicitly, implicitly)

    val id = createUniqueId
    requestStarted(id, expectedRequestLabels)
    Thread.sleep(1050)
    requestCompleted(id, status)
    eventually(monitor.boundSize shouldBe 2)(patienceConfig, implicitly, implicitly)

    monitor.boundLabels should contain theSameElementsAs (Seq(expectedConnectionLabels, expectedRequestLabels))

    val requestBoundProbes = monitor.probes(expectedRequestLabels)
    requestBoundProbes.value.requestCounterProbe.receiveMessage() should be(Inc(1L))
    inside(requestBoundProbes.value.requestTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value shouldBe 1000L +- 100L
    }

    val connectionBoundProbes = monitor.probes(expectedConnectionLabels)
    connectionBoundProbes.value.connectionCounterProbe.receiveMessage() should be(Inc(1L))
    connectionCompleted(expectedConnectionLabels)
    connectionBoundProbes.value.connectionCounterProbe.receiveMessage() should be(Dec(1L))

  }

  it should "reuse monitors for same labels" in testCaseWith(_.withCaching) { implicit c =>
    val expectedConnectionLabels = List(
      ConnectionLabels(None, "0.0.0.0", 8080),
      ConnectionLabels(None, "0.0.0.0", 8081)
    )

    val expectedRequestLabels = List(
      RequestLabels(None, "/api/v1/test", "GET", "200"),
      RequestLabels(None, "/api/v2/test", "POST", "201")
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

    monitor.globalRequestCounter.receiveMessages(requestCount * expectedRequestLabels.size)
    monitor.globalConnectionCounter.receiveMessages(2 * expectedConnectionLabels.size)

    monitor.binds should be(expectedConnectionLabels.size + expectedRequestLabels.size)
  }

  it should "collect metric for several concurrent requests" in testCaseWith(_.withCaching) { implicit c =>
    val connectionLabels = List.tabulate(10)(i => ConnectionLabels(None, "0.0.0.0", 8080 + i))
    connectionLabels.foreach(connectionStarted)

    val requestLabels = List.fill(10)(createUniqueId).map(id => RequestLabels(None, id, "GET", "204"))
    val requests      = requestLabels.map(l => createUniqueId -> l).toMap
    requests.foreach(Function.tupled(requestStarted))
    Thread.sleep(1050)
    requests.foreach { case (id, labels) =>
      requestCompleted(id, labels.status)
    }

    connectionLabels.foreach(connectionCompleted)

    monitor.globalConnectionCounter.receiveMessages(connectionLabels.size)
    monitor.globalRequestCounter.receiveMessages(requests.size)

    monitor.boundLabels should contain theSameElementsAs (connectionLabels ++ requestLabels)

    val connectionProbes = connectionLabels.flatMap(monitor.probes)
    connectionProbes should have size connectionLabels.size
    forAll(connectionProbes) { probes =>
      import probes._
      connectionCounterProbe.within(500 milliseconds) {
        connectionCounterProbe.receiveMessage() should (be(Dec(1L)) or be(Inc(1L)))
      }
    }

    val requestProbes = requestLabels.flatMap(monitor.probes)
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
