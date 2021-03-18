package io.scalac.extension

import java.util.concurrent.atomic.{ AtomicInteger, AtomicLong, AtomicReference }

import scala.concurrent.duration._

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.core.model._
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.ActorEventsMonitorActorTest._
import io.scalac.extension.actor.{ ActorMetrics, MutableActorMetricsStorage }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TestCase._
import io.scalac.extension.util.TimeSeries.LongTimeSeries
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserved, MetricObserverCommand, MetricRecorded }
import io.scalac.extension.util.probe.ObserverCollector.CommonCollectorImpl

class ActorEventsMonitorActorTest
    extends AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorWithServiceTestCaseFactory
    with FreshActorSystemTestCaseFactory {

  type Monitor          = ActorMonitorTestProbe
  override type Context = TestContext[Monitor]

  private val pingOffset: FiniteDuration     = 1.seconds
  private val reasonableTime: FiniteDuration = 3 * pingOffset
  implicit def timeout: Timeout              = pingOffset

  protected val serviceKey: ServiceKey[_] = actorServiceKey

  protected def createMonitor(implicit system: ActorSystem[_]): ActorMonitorTestProbe =
    new ActorMonitorTestProbe(new CommonCollectorImpl(pingOffset))

  protected def createMonitorBehavior(implicit
    c: TestContext[Monitor]
  ): Behavior[_] =
    ActorEventsMonitorActor(
      monitor,
      None,
      pingOffset,
      MutableActorMetricsStorage.empty,
      system.systemActorOf(Behaviors.ignore[AkkaStreamMonitoring.Command], createUniqueId),
      actorMetricsReader = c.TestActorMetricsReader,
      actorTreeTraverser = c.TestActorTreeTraverser
    )

  override protected def createContextFromMonitor(monitor: ActorMonitorTestProbe)(implicit
    system: ActorSystem[_]
  ): TestContext[ActorMonitorTestProbe] =
    new TestContext[Monitor](monitor)

  override protected def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    ActorPathOps.getPathString(ref).startsWith(ActorPathOps.getPathString(parent))

  "ActorEventsMonitor" should "record mailbox size" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(bound.mailboxSizeProbe, c.fakeMailboxSize, c.fakeMailboxSize += 1)
    bound.unbind()
  }

  it should "dead actors should not report" in testCase { implicit c =>
    val tmp   = system.systemActorOf[Nothing](Behaviors.ignore, "tmp")
    val bound = monitor.bind(Labels(ActorPathOps.getPathString(tmp)))
    shouldObserve(bound.mailboxSizeProbe, c.fakeMailboxSize, c.fakeMailboxSize += 1)
    tmp.unsafeUpcast[Any] ! PoisonPill
    bound.mailboxSizeProbe.expectTerminated(tmp)
    bound.mailboxSizeProbe.expectNoMessage(reasonableTime)
    bound.unbind()
  }

  it should "record stash size" in testCase { implicit c =>
    val stashActor = system.systemActorOf(StashActor(10), "stashActor")
    val bound      = monitor.bind(Labels(ActorPathOps.getPathString(stashActor)))
    def stashMeasurement(size: Int): Unit =
      EventBus(system).publishEvent(StashMeasurement(size, ActorPathOps.getPathString(stashActor)))
    stashActor ! Message("random")
    stashMeasurement(1)
    bound.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor ! Message("42")
    stashMeasurement(2)
    bound.stashSizeProbe.expectMessage(MetricRecorded(2))
    stashActor ! Open
    stashMeasurement(0)
    bound.stashSizeProbe.expectMessage(MetricRecorded(0))
    stashActor ! Close
    stashActor ! Message("emanuel")
    stashMeasurement(1)
    bound.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor.unsafeUpcast[Any] ! PoisonPill
    bound.stashSizeProbe.expectTerminated(stashActor)
    bound.unbind()
  }

  it should "record avg mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.mailboxTimeAvgProbe,
      c.fakeMailboxTime.avg,
      c.fakeMailboxTime.copy(avg = c.fakeMailboxTime.avg + 1)
    )
    bound.unbind()
  }

  it should "record min mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.mailboxTimeMinProbe,
      c.fakeMailboxTime.min,
      c.fakeMailboxTime = c.fakeMailboxTime.copy(min = c.fakeMailboxTime.min + 1)
    )
    bound.unbind()
  }

  it should "record max mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.mailboxTimeMaxProbe,
      c.fakeMailboxTime.max,
      c.fakeMailboxTime = c.fakeMailboxTime.copy(max = c.fakeMailboxTime.max + 1)
    )
    bound.unbind()
  }

  it should "record sum mailbox time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.mailboxTimeSumProbe,
      c.fakeMailboxTime.sum,
      c.fakeMailboxTime = c.fakeMailboxTime.copy(sum = c.fakeMailboxTime.sum + 1)
    )
    bound.unbind()
  }

  it should "record received messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(bound.receivedMessagesProbe, c.fakeReceivedMessages, c.fakeReceivedMessages += 1)
    bound.unbind()
  }

  it should "record processed messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(bound.processedMessagesProbe, c.fakeProcessedMessages, c.fakeProcessedMessages += 1)
    bound.unbind()
  }

  it should "record failed messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(bound.failedMessagesProbe, c.fakeFailedMessages, c.fakeFailedMessages += 1)
    bound.unbind()
  }

  it should "record avg processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.processingTimeAvgProbe,
      c.fakeProcessingTimes.avg,
      c.fakeProcessingTimes = c.fakeProcessingTimes.copy(avg = c.fakeProcessingTimes.avg + 1)
    )
    bound.unbind()
  }

  it should "record min processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.processingTimeMinProbe,
      c.fakeProcessingTimes.min,
      c.fakeProcessingTimes = c.fakeProcessingTimes.copy(min = c.fakeProcessingTimes.min + 1)
    )
    bound.unbind()
  }

  it should "record max processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.processingTimeMaxProbe,
      c.fakeProcessingTimes.max,
      c.fakeProcessingTimes = c.fakeProcessingTimes.copy(max = c.fakeProcessingTimes.max + 1)
    )
    bound.unbind()
  }

  it should "record sum processing time" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(
      bound.processingTimeSumProbe,
      c.fakeProcessingTimes.sum,
      c.fakeProcessingTimes = c.fakeProcessingTimes.copy(sum = c.fakeProcessingTimes.sum + 1)
    )
    bound.unbind()
  }

  it should "record the sent messages" in testCase { implicit c =>
    val bound = monitor.bind(Labels("/"))
    shouldObserve(bound.sentMessagesProbe, c.fakeSentMessages, c.fakeSentMessages += 1)
    bound.unbind()
  }

  def shouldObserve[T](probe: TestProbe[MetricObserverCommand], metric: => Long, change: => Unit): Unit = {
    probe.expectMessage(reasonableTime, MetricObserved(metric))
    change
    probe.expectMessage(reasonableTime, MetricObserved(metric))
  }

}

object ActorEventsMonitorActorTest {

  final case class TestContext[+M](monitor: M)(implicit val system: ActorSystem[_]) extends MonitorTestCaseContext[M] {
    import scala.language.implicitConversions

    val TestActorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser

    var fakeMailboxSize       = 10
    var fakeReceivedMessages  = 12
    var fakeProcessedMessages = 10
    var fakeFailedMessages    = 2
    var fakeSentMessages      = 10

    var fakeMailboxTime: LongValueAggMetric = LongValueAggMetric(1, 2, 1, 4, 3)

    var fakeProcessingTimes: LongValueAggMetric = LongValueAggMetric(1, 2, 1, 4, 3)

    def fakeUnhandledMessages: Long = fakeReceivedMessages - fakeProcessedMessages

    val TestActorMetricsReader: ActorMetricsReader = { _ =>
      Some(
        ActorMetrics(
          mailboxSize = Some(fakeMailboxSize),
          mailboxTime = Some(fakeMailboxTime),
          receivedMessages = Some(fakeReceivedMessages),
          unhandledMessages = Some(fakeUnhandledMessages),
          failedMessages = Some(fakeFailedMessages),
          processingTime = Some(fakeProcessingTimes),
          sentMessages = Some(fakeSentMessages)
        )
      )
    }

  }

  sealed trait Command
  final case object Open                 extends Command
  final case object Close                extends Command
  final case class Message(text: String) extends Command

  object StashActor {
    def apply(capacity: Int): Behavior[Command] =
      Behaviors.withStash(capacity)(buffer => new StashActor(buffer).closed())
  }

  class StashActor(buffer: StashBuffer[Command]) {
    private def closed(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case msg =>
          buffer.stash(msg)
          Behaviors.same
      }

    private def open(): Behavior[Command] = Behaviors.receiveMessagePartial {
      case Close =>
        closed()
      case Message(_) =>
        Behaviors.same
    }

  }

}
