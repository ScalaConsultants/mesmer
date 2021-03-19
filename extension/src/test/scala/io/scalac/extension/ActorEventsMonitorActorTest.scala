package io.scalac.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.javadsl.FishingOutcomes
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.{ Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.util.Timeout
import io.scalac.core.model._
import io.scalac.core.util.ActorPathOps
import io.scalac.extension.ActorEventsMonitorActor._
import io.scalac.extension.ActorEventsMonitorActorTest._
import io.scalac.extension.actor.{ ActorMetrics, MailboxTime, MutableActorMetricsStorage }
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import io.scalac.extension.metric.ActorMetricMonitor.Labels
import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TestCase._
import io.scalac.extension.util.TimeSeries.LongTimeSeries
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.BoundTestProbe.{ MetricObserved, MetricObserverCommand, MetricRecorded }
import io.scalac.extension.util.probe.ObserverCollector.CommonCollectorImpl
import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class ActorEventsMonitorActorTest
    extends AnyFlatSpecLike
    with Matchers
    with Inspectors
    with MonitorWithBasicContextAndServiceTestCaseFactory
    with FreshActorSystemTestCaseFactory {

  type Monitor = ActorMonitorTestProbe

  private val pingOffset: FiniteDuration     = 1.seconds
  private val reasonableTime: FiniteDuration = 3 * pingOffset
  implicit def timeout: Timeout              = pingOffset

  protected val serviceKey: ServiceKey[_] = actorServiceKey

  protected def createMonitor(implicit system: ActorSystem[_]): ActorMonitorTestProbe =
    ActorMonitorTestProbe(new CommonCollectorImpl(pingOffset))

  protected def createMonitorBehavior(implicit
    c: MonitorTestCaseContext.BasicContext[ActorMonitorTestProbe]
  ): Behavior[_] =
    ActorEventsMonitorActor(
      monitor,
      None,
      pingOffset,
      MutableActorMetricsStorage.empty,
      system.systemActorOf(Behaviors.ignore[AkkaStreamMonitoring.Command], createUniqueId),
      actorMetricsReader = TestActorMetricsReader,
      actorTreeTraverser = TestActorTreeTraverser
    )

  override protected def testSameOrParent(ref: ActorRef[_], parent: ActorRef[_]): Boolean =
    ActorPathOps.getPathString(ref).startsWith(ActorPathOps.getPathString(parent))

  private val CommonLabels: Labels = Labels("/")

  "ActorEventsMonitor" should "record mailbox size" in testCase { implicit c =>
    recordMailboxSize(5, CommonLabels, monitor)
  }

  it should "record mailbox size changes" in testCase { implicit c =>
    recordMailboxSize(5, CommonLabels, monitor)
    recordMailboxSize(10, CommonLabels, monitor)
  }

  it should "dead actors should not report" in testCase { implicit c =>
    val tmp    = system.systemActorOf[Nothing](Behaviors.ignore, "tmp")
    val labels = Labels(ActorPathOps.getPathString(tmp))
    recordMailboxSize(5, labels, monitor)
    tmp.unsafeUpcast[Any] ! PoisonPill
    monitor.mailboxSizeProbe.expectTerminated(tmp)

    assertThrows[AssertionError]( //TODO fix this to better implementation
      monitor.mailboxSizeProbe.fishForMessage(reasonableTime) { case MetricObserved(_, `labels`) =>
        FishingOutcomes.complete()
      }
    )
  }

  it should "record stash size" in testCase { implicit c =>
    val stashActor = system.systemActorOf(StashActor(10), "stashActor")
    val actorPath  = ActorPathOps.getPathString(stashActor)

    def stashMeasurement(size: Int): Unit =
      EventBus(system).publishEvent(StashMeasurement(size, actorPath))

    stashActor ! Message("random")
    stashMeasurement(1)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor ! Message("42")
    stashMeasurement(2)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(2))
    stashActor ! Open
    stashMeasurement(0)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(0))
    stashActor ! Close
    stashActor ! Message("emanuel")
    stashMeasurement(1)
    monitor.stashSizeProbe.expectMessage(MetricRecorded(1))
    stashActor.unsafeUpcast[Any] ! PoisonPill
    monitor.stashSizeProbe.expectTerminated(stashActor)

  }

  it should "record avg mailbox time" in testCase { implicit c =>
    recordMailboxTime(FakeMailboxTime, CommonLabels, monitor.mailboxTimeAvgProbe)
  }

  it should "record min mailbox time" in testCase { implicit c =>
    recordMailboxTime(FakeMailboxTime / 2, CommonLabels, monitor.mailboxTimeMinProbe)
  }

  it should "record max mailbox time" in testCase { implicit c =>
    recordMailboxTime(FakeMailboxTime * 2, CommonLabels, monitor.mailboxTimeMaxProbe)
  }

  it should "record sum mailbox time" in testCase { implicit c =>
    recordMailboxTime(FakeMailboxTimes.reduce(_ + _), CommonLabels, monitor.mailboxTimeSumProbe)
  }

  it should "record received messages" in testCase { implicit c =>
    monitor.receivedMessagesProbe.fishForMessage(reasonableTime) {
      case MetricObserved(FakeReceivedMessages, CommonLabels) => FishingOutcomes.complete()
      case _                                                  => FishingOutcomes.continueAndIgnore()
    }
  }

  it should "record processed messages" in testCase { implicit c =>
    monitor.processedMessagesProbe
      .fishForMessage(reasonableTime) {
        case MetricObserved(FakeProcessedMessages, CommonLabels) => FishingOutcomes.complete()
        case _                                                   => FishingOutcomes.continueAndIgnore()
      }
  }

  it should "record failed messages" in testCase { implicit c =>
    monitor.failedMessagesProbe
      .fishForMessage(reasonableTime) {
        case MetricObserved(FakeFailedMessages, CommonLabels) => FishingOutcomes.complete()
        case _                                                => FishingOutcomes.continueAndIgnore()
      }
  }

  def recordMailboxSize(n: Int, labels: Labels, monitor: ActorMonitorTestProbe): Unit = {
    FakeMailboxSize = n
    monitor.mailboxSizeProbe.fishForMessage(reasonableTime) {
      case MetricObserved(`n`, `labels`) => FishingOutcomes.complete()
      case _                             => FishingOutcomes.continueAndIgnore()
    }
  }

  def recordMailboxTime(d: FiniteDuration, labels: Labels, probe: TestProbe[MetricObserverCommand[Labels]]): Unit =
    probe.fishForMessage(reasonableTime) {
      case MetricObserved(duration, `labels`) if duration == d.toMillis => FishingOutcomes.complete()
      case _                                                            => FishingOutcomes.continueAndIgnore()
    }

}

object ActorEventsMonitorActorTest {

  @volatile
  private var FakeMailboxSize = 10
  private val FakeMailboxTime = 1.second
  // min: FakeMailboxTime / 2  |  avg: FakeMailboxTime  |  max: 2 * MailboxTime
  private val FakeMailboxTimes                   = Array(FakeMailboxTime / 2, FakeMailboxTime / 2, 2 * FakeMailboxTime)
  val TestActorTreeTraverser: ActorTreeTraverser = ReflectiveActorTreeTraverser
  private val FakeReceivedMessages               = 12
  private val FakeProcessedMessages              = 10
  private val FakeUnhandledMessages              = FakeReceivedMessages - FakeProcessedMessages
  private val FakeFailedMessages                 = 2

  val TestActorMetricsReader: ActorMetricsReader = { _ =>
    Some(
      ActorMetrics(
        mailboxSize = Some(FakeMailboxSize),
        mailboxTime = Some(LongValueAggMetric.fromTimeSeries(new LongTimeSeries(FakeMailboxTimes.map(MailboxTime(_))))),
        receivedMessages = Some(FakeReceivedMessages),
        unhandledMessages = Some(FakeUnhandledMessages),
        failedMessages = Some(FakeFailedMessages)
      )
    )
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
