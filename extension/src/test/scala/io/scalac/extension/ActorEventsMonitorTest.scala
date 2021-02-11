package io.scalac.extension

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.extension.ActorEventsMonitor.{ ActorTreeRunner, ReflectiveActorTreeRunner }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.CachingMonitor
import io.scalac.extension.util.TestConfig
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorded
import io.scalac.extension.util.{ MonitorFixture, TestOps }

class ActorEventsMonitorTest
    extends ScalaTestWithActorTestKit(ActorEventsMonitorTest.system)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorFixture
    with TestOps {

  private val MonitorPingOffset = 2.seconds

  type Monitor = ActorMonitorTestProbe

  override val serviceKey: ServiceKey[_] = actorServiceKey

  override def createMonitor: ActorMonitorTestProbe = new ActorMonitorTestProbe(MonitorPingOffset)

  override def setUp(monitor: ActorMonitorTestProbe, cache: Boolean): ActorRef[_] = {
    ActorEventsMonitor.start(monitor, system, None)
    system.systemActorOf(
      ActorEventsMonitor.Actor(if (cache) CachingMonitor(monitor) else monitor, None),
      createUniqueId
    )
  }

  testActorTreeRunner(ReflectiveActorTreeRunner)
  testMonitor()

  private val underlyingSystem = system.asInstanceOf[ActorSystem[String]]

  def testActorTreeRunner(actorTreeRunner: ActorTreeRunner): Unit = {

    s"ActorTreeRunner instance (${actorTreeRunner.getClass.getName})" should "getRoot properly" in {
      val root = actorTreeRunner.getRootGuardian(system.classicSystem)
      root.path.toStringWithoutAddress should be("/")
    }

    it should "getChildren properly" in {
      val root     = actorTreeRunner.getRootGuardian(system.classicSystem)
      val children = actorTreeRunner.getChildren(root)
      children.map(_.path.toStringWithoutAddress) should contain theSameElementsAs (Set(
        "/system",
        "/user"
      ))
    }

    it should "getChildren properly from nested actor" in {
      val root             = actorTreeRunner.getRootGuardian(system.classicSystem)
      val children         = actorTreeRunner.getChildren(root)
      val guardian         = children.find(_.path.toStringWithoutAddress == "/user").get
      val guardianChildren = actorTreeRunner.getChildren(guardian)
      guardianChildren.map(_.path.toStringWithoutAddress) should contain theSameElementsAs Set(
        "/user/actorA",
        "/user/actorB"
      )
    }

  }

  def testMonitor(): Unit = {

    "ActorEventsMonitor" should "record mailbox size" in test { monitor =>
      val n = 10
      underlyingSystem ! "idle"
      for (_ <- 0 until n) underlyingSystem ! "Record it"
      val bound = monitor.bind(Labels("/user/actorB/idle", None))
//      bound.mailboxSizeProbe
//        .awaitAssert(bound.mailboxSizeProbe.expectMessage(MetricRecorded(n)), 3 * MonitorPingOffset)
      val msgs = bound.mailboxSizeProbe.fishForMessage(3 * MonitorPingOffset) {
        case MetricRecorded(`n`) => FishingOutcome.Complete
        case _                   => FishingOutcome.ContinueAndIgnore
      }
      msgs.size should not be (0)
    }

    it should "record stash size" in test { monitor =>
      val stashActor = system.systemActorOf(StashActor(10), "stashActor")
      val bound      = monitor.bind(Labels(stashActor.ref.path.toStringWithoutAddress, None))
      def stashMeasurement(size: Int): Unit =
        EventBus(system).publishEvent(StashMeasurement(size, stashActor.ref.path.toStringWithoutAddress))
      stashActor ! "random"
      stashMeasurement(1)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(1)))
      stashActor ! "42"
      stashMeasurement(2)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(2)))
      stashActor ! "open"
      stashMeasurement(0)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(0)))
      stashActor ! "close"
      stashActor ! "emanuel"
      stashMeasurement(1)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(1)))
    }
  }

  object StashActor {
    def apply(capacity: Int): Behavior[String] =
      Behaviors.withStash(capacity)(buffer => new StashActor(buffer).closed())
  }

  class StashActor(buffer: StashBuffer[String]) {
    private def closed(): Behavior[String] =
      Behaviors.receiveMessage {
        case "open" =>
          buffer.unstashAll(open())
        case msg =>
          println(s"[typed] [stashing] $msg")
          buffer.stash(msg)
          Behaviors.same
      }

    private def open(): Behavior[String] = Behaviors.receiveMessage {
      case "close" =>
        closed()
      case msg =>
        println(s"[typed] [working on] $msg")
        Behaviors.same
    }

  }

}

object ActorEventsMonitorTest {

  val system: ActorSystem[String] = ActorSystem(
    Behaviors.setup[String] { ctx =>
      val actorA = ctx.spawn[String](
        Behaviors.setup[String] { ctx =>
          import ctx.log
          Behaviors.receiveMessage { msg =>
            log.info(s"[actorA] received a message: $msg")

            Behaviors.same
          }
        },
        "actorA"
      )

      val actorB = ctx.spawn[String](
        Behaviors.setup { ctx =>
          import ctx.log

          val actorBIdle = ctx.spawn[String](
            Behaviors.setup { ctx =>
              import ctx.log
              Behaviors.receiveMessage {
                case "idle" =>
                  log.info("[idle] ...")
                  Thread.sleep(5.seconds.toMillis)
                  Behaviors.same
                case _ =>
                  Behaviors.same
              }
            },
            "idle"
          )

          Behaviors.receiveMessage { msg =>
            log.info(s"[actorB] received a message: $msg")
            actorBIdle ! msg
            Behaviors.same
          }
        },
        "actorB"
      )

      Behaviors.receiveMessage { msg =>
        actorA ! msg
        actorB ! msg
        Behaviors.same
      }
    },
    "ActorEventsMonitorTest",
    TestConfig.localActorProvider
  )

}
