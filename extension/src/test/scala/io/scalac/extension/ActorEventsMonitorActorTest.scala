package io.scalac.extension

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import io.scalac.extension.ActorEventsMonitorActor.{ ActorTreeTraverser, ReflectiveActorTreeTraverser }
import io.scalac.extension.ActorEventsMonitorActorTest._
import io.scalac.extension.actor.MutableActorMetricsStorage
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.metric.{ ActorMetricMonitor, CachingMonitor }
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.ActorMonitorTestProbe.TestBoundMonitor
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserved, MetricRecorded }
import io.scalac.extension.util.{ MonitorFixture, TestConfig, TestOps }
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ActorEventsMonitorActorTest
    extends ScalaTestWithActorTestKit(testSystem)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorFixture
    with TestOps {

  type Monitor      = ActorMonitorTestProbe
  type Command      = ActorEventsMonitorActor.Command
  type ActorCommand = ActorEventsMonitorActorTest.Command

  private val PingOffset       = 2.seconds
  private val underlyingSystem = system.asInstanceOf[ActorSystem[ActorCommand]]
  override val serviceKey      = Some(actorServiceKey)

  override def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    ref.path.toStringWithoutAddress.startsWith(parent.path.toStringWithoutAddress)

  override def createMonitor: ActorMonitorTestProbe = new ActorMonitorTestProbe(PingOffset)

  override def setUp(monitor: ActorMonitorTestProbe, cache: Boolean): ActorRef[Command] =
    system.systemActorOf(
      ActorEventsMonitorActor(
        if (cache) CachingMonitor(monitor) else monitor,
        None,
        PingOffset,
        MutableActorMetricsStorage.empty,
        system.systemActorOf(Behaviors.ignore[AkkaStreamMonitoring.Command], createUniqueId)
      ),
      createUniqueId
    )

  // ** MAIN **
  testActorTreeRunner(ReflectiveActorTreeTraverser)
  testMonitor()

  def testActorTreeRunner(actorTreeRunner: ActorTreeTraverser): Unit = {

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

    def recordMailboxSize(n: Int, bound: TestBoundMonitor): Unit = {
      underlyingSystem ! Idle
      for (_ <- 0 until n) underlyingSystem ! Message("Record it")
      val records = bound.mailboxSizeProbe.fishForMessage(3 * PingOffset) {
        case MetricObserved(`n`) => FishingOutcome.Complete
        case _                   => FishingOutcome.ContinueAndIgnore
      }
      records.size should not be (0)
    }

    "ActorEventsMonitor" should "record mailbox size" in test { monitor =>
      val bound = monitor.bind(ActorMetricMonitor.Labels("/user/actorB/idle", None))
      recordMailboxSize(10, bound)
      bound.unbind()
    }

    it should "record mailbox size changes" in test { monitor =>
      val bound = monitor.bind(ActorMetricMonitor.Labels("/user/actorB/idle", None))
      recordMailboxSize(10, bound)
      Thread.sleep((IdleTime + 1.second).toMillis)
      recordMailboxSize(42, bound)
      bound.unbind()
    }

    it should "dead actors should not report" in test { monitor =>
      // record mailbox for a cycle
      val bound = monitor.bind(ActorMetricMonitor.Labels("/user/actorA/stop", None))
      bound.mailboxSizeProbe.expectMessageType[MetricObserved](2 * PingOffset)
      // send poison pill to kill actor
      underlyingSystem ! Stop
      Thread.sleep(PingOffset.toMillis)
      bound.mailboxSizeProbe.expectNoMessage()
    }

    it should "record stash size" in test { monitor =>
      val stashActor = system.systemActorOf(StashActor(10), "stashActor")
      val bound      = monitor.bind(Labels(stashActor.ref.path.toStringWithoutAddress, None))
      def stashMeasurement(size: Int): Unit =
        EventBus(system).publishEvent(StashMeasurement(size, stashActor.ref.path.toStringWithoutAddress))
      stashActor ! Message("random")
      stashMeasurement(1)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(1)))
      stashActor ! Message("42")
      stashMeasurement(2)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(2)))
      stashActor ! Open
      stashMeasurement(0)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(0)))
      stashActor ! Close
      stashActor ! Message("emanuel")
      stashMeasurement(1)
      bound.stashSizeProbe.awaitAssert(bound.stashSizeProbe.expectMessage(MetricRecorded(1)))
    }

  }

  object StashActor {
    def apply(capacity: Int): Behavior[ActorCommand] =
      Behaviors.withStash(capacity)(buffer => new StashActor(buffer).closed())
  }

  class StashActor(buffer: StashBuffer[ActorCommand]) {
    private def closed(): Behavior[ActorCommand] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case msg @ Message(text) =>
          println(s"[typed] [stashing] {}", text)
          buffer.stash(msg)
          Behaviors.same
      }

    private def open(): Behavior[ActorCommand] = Behaviors.receiveMessagePartial {
      case Close =>
        closed()
      case Message(text) =>
        println(s"[typed] [working on] {}", text)
        Behaviors.same
    }

  }

}

object ActorEventsMonitorActorTest {

  val IdleTime: FiniteDuration = 3.seconds

  sealed trait Command
  final case object Idle                 extends Command
  final case object Open                 extends Command
  final case object Close                extends Command
  final case object Stop                 extends Command
  final case class Message(text: String) extends Command

  val testSystem: ActorSystem[Command] = ActorSystem(
    Behaviors.setup[Command] { ctx =>
      val actorA = ctx.spawn[Command](
        Behaviors.setup[Command] { ctx =>
          import ctx.log

          val actorAStop = ctx.spawn[Command](
            Behaviors.setup { ctx =>
              import ctx.log
              Behaviors.receiveMessage {
                case Stop =>
                  Behaviors.stopped
                case msg =>
                  log.info(s"[actorA] received a message: {}", msg)
                  Behaviors.same
              }
            },
            "stop"
          )

          Behaviors.receiveMessage { msg =>
            log.info(s"[actorA] received a message: {}", msg)
            actorAStop ! msg
            Behaviors.same
          }
        },
        "actorA"
      )

      val actorB = ctx.spawn[Command](
        Behaviors.setup { ctx =>
          import ctx.log

          val actorBIdle = ctx.spawn[Command](
            Behaviors.setup { ctx =>
              import ctx.log
              Behaviors.receiveMessage {
                case Idle =>
                  log.info("[idle] ...")
                  Thread.sleep(IdleTime.toMillis)
                  Behaviors.same
                case _ =>
                  Behaviors.same
              }
            },
            "idle"
          )

          Behaviors.receiveMessage { cmd =>
            log.info(s"[actorB] received a message: {}", cmd)
            actorBIdle ! cmd
            Behaviors.same
          }
        },
        "actorB"
      )

      Behaviors.receiveMessage { cmd =>
        actorA ! cmd
        actorB ! cmd
        Behaviors.same
      }
    },
    "ActorEventsMonitorTest",
    TestConfig.localActorProvider
  )

}
