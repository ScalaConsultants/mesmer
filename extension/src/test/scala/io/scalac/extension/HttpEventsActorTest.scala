package io.scalac.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Listing
import akka.actor.typed.scaladsl.AskPattern._
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent.{ RequestCompleted, RequestStarted }
import io.scalac.extension.metric.HttpMetricMonitor.Labels
import io.scalac.extension.util.BoundTestProbe._
import io.scalac.extension.util.{ HttpMetricsTestProbe, IdentityPathService, TerminationRegistryOps, TestOps }
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.matchers.{ MatchResult, Matcher }

import scala.concurrent.duration._
import scala.language.postfixOps

class HttpEventsActorTest
    extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with Eventually
    with OptionValues
    with Inside
    with BeforeAndAfterAll
    with TerminationRegistryOps
    with LoneElement
    with TestOps {

  type Fixture = HttpMetricsTestProbe

  def sameOrParent(ref: ActorRef[_]): Matcher[ActorRef[_]] = left => {
    val test = ref.path == left.path || left.path.parent == ref.path

    MatchResult(test, s"${ref} is not same or parent of ${left}", s"${ref} is same as or parent of ${left}")
  }

  def test(body: Fixture => Any): Any = {
    val monitor = new HttpMetricsTestProbe()
    val sut     = system.systemActorOf(HttpEventsActor(monitor, IdentityPathService), createUniqueId)
    watch(sut)
    //Receptionist has a delay between registering and deregistering services, here we wait for cleanup of other tests
    eventually {
      val result = Receptionist(system).ref.ask[Listing](reply => Receptionist.find(httpServiceKey, reply)).futureValue
      inside(result) {
        case httpServiceKey.Listing(res) => {
          val elem = res.loneElement
          elem should sameOrParent(sut)
        }
      }
    }
    body(monitor)
    sut.unsafeUpcast[Any] ! PoisonPill
    waitFor(sut)
  }

  def requestStarted(id: String, labels: Labels): Unit = EventBus(system).publishEvent(
    RequestStarted(id, System.currentTimeMillis(), labels.path, labels.method)
  )

  def requestCompleted(id: String): Unit =
    EventBus(system).publishEvent(RequestCompleted(id, System.currentTimeMillis()))

  "HttpEventsActor" should "collect metrics for single request" in test { monitor =>
    val expectedLabels = Labels(None, "/api/v1/test", "GET")

    val id = createUniqueId
    EventBus(system).publishEvent(
      RequestStarted(id, System.currentTimeMillis(), expectedLabels.path, expectedLabels.method)
    )
    Thread.sleep(1050)
    EventBus(system).publishEvent(RequestCompleted(id, System.currentTimeMillis()))

    eventually(monitor.boundSize shouldBe 1)

    monitor.boundLabels should contain theSameElementsAs (Seq(expectedLabels))
    val boundProbes = monitor.probes(expectedLabels)

    boundProbes.value.requestCounterProbe.receiveMessage() should be(Inc(1L))
    inside(boundProbes.value.requestTimeProbe.receiveMessage()) {
      case MetricRecorded(value) => value shouldBe 1000L +- 100L
    }
  }

  it should "reuse monitors for same labels" in test { monitor =>
    val expectedLabels = List(Labels(None, "/api/v1/test", "GET"), Labels(None, "/api/v2/test", "POST"))
    val requestCount   = 10

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

  it should "collect metric for several concurrent requests" in test { monitor =>
    val labels   = List.fill(10)(createUniqueId).map(id => Labels(None, id, "GET"))
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
      inside(requestTimeProbe.receiveMessage()) {
        case MetricRecorded(value) => value shouldBe 1000L +- 100L
      }
    }
  }

}
