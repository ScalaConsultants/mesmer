package io.scalac.mesmer.extension
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.ServiceKey
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.language.postfixOps

import io.scalac.mesmer.core._
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.HttpEvent.ConnectionCompleted
import io.scalac.mesmer.core.event.HttpEvent.ConnectionStarted
import io.scalac.mesmer.core.event.HttpEvent.RequestCompleted
import io.scalac.mesmer.core.event.HttpEvent.RequestStarted
import io.scalac.mesmer.core.model
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.TestCase.CommonMonitorTestFactory
import io.scalac.mesmer.core.util.TestCase.MonitorTestCaseContext.BasicContext
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.core.util._
import io.scalac.mesmer.extension.http.MutableRequestStorage
import io.scalac.mesmer.extension.metric.CachingMonitor
import io.scalac.mesmer.extension.metric.HttpConnectionMetricsMonitor
import io.scalac.mesmer.extension.metric.HttpMetricsMonitor
import io.scalac.mesmer.extension.util.IdentityPathService
import io.scalac.mesmer.extension.util.probe.BoundTestProbe._
import io.scalac.mesmer.extension.util.probe.HttpConnectionMetricsTestProbe
import io.scalac.mesmer.extension.util.probe.HttpMonitorTestProbe

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

  type Monitor = MonitorImpl
  type Command = HttpEventsActor.Event
  case class MonitorImpl(request: HttpMonitorTestProbe, connection: HttpConnectionMetricsTestProbe)

  protected val serviceKey: ServiceKey[_] = httpServiceKey

  protected def createMonitorBehavior(implicit context: BasicContext[Monitor]): Behavior[Command] = {
    val MonitorImpl(requestMonitor, connectionMonitor) = monitor
    HttpEventsActor(
      if (context.caching) CachingMonitor(requestMonitor) else requestMonitor,
      if (context.caching) CachingMonitor(connectionMonitor) else connectionMonitor,
      MutableRequestStorage.empty,
      IdentityPathService
    )
  }

  protected def createMonitor(implicit s: ActorSystem[_]): Monitor =
    MonitorImpl(new HttpMonitorTestProbe()(s), new HttpConnectionMetricsTestProbe()(s))

  def connectionStarted(attributes: HttpConnectionMetricsMonitor.Attributes): Unit =
    EventBus(system).publishEvent(ConnectionStarted(attributes.interface, attributes.port))

  def connectionCompleted(attributes: HttpConnectionMetricsMonitor.Attributes): Unit =
    EventBus(system).publishEvent(ConnectionCompleted(attributes.interface, attributes.port))

  def requestStarted(id: String, attributes: HttpMetricsMonitor.Attributes): Unit =
    EventBus(system).publishEvent(RequestStarted(id, Timestamp.create(), attributes.path, attributes.method))

  def requestCompleted(id: String, status: model.Status): Unit =
    EventBus(system).publishEvent(RequestCompleted(id, Timestamp.create(), status))

  "HttpEventsActor" should "collect metrics for single request" in testCase { implicit c =>
    val status: model.Status         = "200"
    val expectedConnectionAttributes = HttpConnectionMetricsMonitor.Attributes(None, "0.0.0.0", 8080)
    val expectedRequestAttributes    = HttpMetricsMonitor.Attributes(None, "/api/v1/test", "GET", status)

    connectionStarted(expectedConnectionAttributes)
    eventually(monitor.connection.boundSize shouldBe 1)(patienceConfig, implicitly, implicitly)

    val id = createUniqueId
    requestStarted(id, expectedRequestAttributes)
    Thread.sleep(1050)
    requestCompleted(id, status)
    eventually(monitor.request.boundSize shouldBe 1)(patienceConfig, implicitly, implicitly)

    monitor.connection.boundAttributes should contain theSameElementsAs Seq(expectedConnectionAttributes)
    monitor.request.boundAttributes should contain theSameElementsAs Seq(expectedRequestAttributes)

    val requestBoundProbes = monitor.request.probes(expectedRequestAttributes)
    requestBoundProbes.value.requestCounterProbe.receiveMessage() should be(Inc(1L))
    inside(requestBoundProbes.value.requestTimeProbe.receiveMessage()) { case MetricRecorded(value) =>
      value shouldBe 1000L +- 100L
    }

    val connectionBoundProbes = monitor.connection.probes(expectedConnectionAttributes)
    connectionBoundProbes.value.connectionCounterProbe.receiveMessage() should be(Inc(1L))
    connectionCompleted(expectedConnectionAttributes)
    connectionBoundProbes.value.connectionCounterProbe.receiveMessage() should be(Dec(1L))

  }

  it should "reuse monitors for same attributes" in testCaseWith(_.withCaching) { implicit c =>
    val expectedConnectionAttributes = List(
      HttpConnectionMetricsMonitor.Attributes(None, "0.0.0.0", 8080),
      HttpConnectionMetricsMonitor.Attributes(None, "0.0.0.0", 8081)
    )

    val expectedRequestAttributes = List(
      HttpMetricsMonitor.Attributes(None, "/api/v1/test", "GET", "200"),
      HttpMetricsMonitor.Attributes(None, "/api/v2/test", "POST", "201")
    )
    val requestCount = 10

    expectedConnectionAttributes.foreach(connectionStarted)

    for {
      attribute <- expectedRequestAttributes
      id        <- List.fill(requestCount)(createUniqueId)
    } {
      requestStarted(id, attribute)
      requestCompleted(id, attribute.status)
    }

    expectedConnectionAttributes.foreach(connectionCompleted)

    monitor.connection.globalConnectionCounter.receiveMessages(2 * expectedConnectionAttributes.size)
    monitor.request.globalRequestCounter.receiveMessages(requestCount * expectedRequestAttributes.size)

    monitor.connection.binds should be(expectedConnectionAttributes.size)
    monitor.request.binds should be(expectedRequestAttributes.size)
  }

  it should "collect metric for several concurrent requests" in testCaseWith(_.withCaching) { implicit c =>
    val connectionAttributes =
      List.tabulate(10)(i => HttpConnectionMetricsMonitor.Attributes(None, "0.0.0.0", 8080 + i))
    connectionAttributes.foreach(connectionStarted)

    val requestAttributes =
      List.fill(10)(createUniqueId).map(id => HttpMetricsMonitor.Attributes(None, id, "GET", "204"))
    val requests = requestAttributes.map(l => createUniqueId -> l).toMap
    requests.foreach(Function.tupled(requestStarted))
    Thread.sleep(1050)
    requests.foreach { case (id, attributes) =>
      requestCompleted(id, attributes.status)
    }

    connectionAttributes.foreach(connectionCompleted)

    monitor.connection.globalConnectionCounter.receiveMessages(connectionAttributes.size)
    monitor.request.globalRequestCounter.receiveMessages(requests.size)

    monitor.connection.boundAttributes should contain theSameElementsAs connectionAttributes
    monitor.request.boundAttributes should contain theSameElementsAs requestAttributes

    val connectionProbes = connectionAttributes.flatMap(monitor.connection.probes)
    connectionProbes should have size connectionAttributes.size
    forAll(connectionProbes) { probes =>
      import probes._
      connectionCounterProbe.within(500 milliseconds) {
        connectionCounterProbe.receiveMessage() should (be(Dec(1L)) or be(Inc(1L)))
      }
    }

    val requestProbes = requestAttributes.flatMap(monitor.request.probes)
    requestProbes should have size requestAttributes.size
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
