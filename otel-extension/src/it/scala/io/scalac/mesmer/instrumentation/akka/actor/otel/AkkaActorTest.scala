package io.scalac.mesmer.instrumentation.akka.actor.otel

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import akka.actor.{ PoisonPill, Props }
import akka.{ actor => classic }
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.data.{ LongPointData, MetricDataType }
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.scalac.mesmer.agent.utils.{ OtelAgentTest, SafeLoadSystem }
import io.scalac.mesmer.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import io.scalac.mesmer.core.akka.model.AttributeNames
import io.scalac.mesmer.core.event.ActorEvent
import io.scalac.mesmer.core.util.ReceptionistOps
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.{ Instruments, InstrumentsProvider }
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{ BeforeAndAfterEach, Inspectors, OptionValues }

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.control.NoStackTrace

final class AkkaActorTest
    extends AnyFlatSpec
    with OtelAgentTest
    with ReceptionistOps
    with OptionValues
    with Eventually
    with Matchers
    with SafeLoadSystem
    with BeforeAndAfterEach
    with Inspectors {

  private val Tolerance: Double = scaled(25.millis).toMillis.toDouble

  @volatile
  private var instruments: Instruments = _
  @volatile
  private var reader: InMemoryMetricReader = _

  override protected def beforeEach() {
    reader = InMemoryMetricReader.create()
    val provider = SdkMeterProvider.builder().registerMetricReader(reader).build()
    instruments = InstrumentsProvider.setInstruments(Instruments(provider))

    super.beforeEach()
  }

  import AkkaActorAgentTest._

  private final val StashMessageCount = 10
  private final val ToleranceNanos    = 20_000_000

  private def publishActorContext[T](
    contextRef: ActorRef[classic.ActorContext]
  )(inner: Behavior[T]): Behavior[T] = Behaviors.setup[T] { ctx =>
    contextRef ! ctx.toClassic
    inner
  }

  private def testEffectWithSupervision[T](effect: T => Unit, strategy: SupervisorStrategy, probes: Boolean)(
    messages: T*
  )(checks: (Int, classic.ActorContext => Any)*): Any =
    testWithChecks[T](
      ref => Behaviors.supervise(effectBehavior(ref, effect)).onFailure(strategy),
      probes
    )(messages: _*)(checks: _*)

  private def testWithChecks[T](inner: ActorRef[Unit] => Behavior[T], probes: Boolean)(
    messages: T*
  )(checks: (Int, classic.ActorContext => Any)*): Any = {

    val contextProbe = createTestProbe[classic.ActorContext]
    val effectProbe  = createTestProbe[Unit]

    val sut =
      system.systemActorOf(
        publishActorContext[T](contextProbe.ref)(inner(effectProbe.ref)),
        createUniqueId
      )

    val ctx = contextProbe.receiveMessage()
    checks.scanLeft(0) { case (skip, (n, check)) =>
      messages.slice(skip, skip + n).foreach(sut.tell)
      if (probes) {
        effectProbe.receiveMessages(n)
        check(ctx)
      } else {
        eventually {
          check(ctx)
        }
      }
      skip + n
    }
  }

  private def effectBehavior[T](ackRef: ActorRef[Unit], effect: T => Unit): Behavior[T] = Behaviors.receiveMessage[T] {
    msg =>
      effect(msg)
      ackRef ! ()
      Behaviors.same
  }

  private def testBehavior[T](behavior: T => Behavior[T])(messages: T*)(
    checks: (Int, classic.ActorContext => Any)*
  ): Any =
    testWithChecks(_ => Behaviors.receiveMessage[T](behavior), false)(messages: _*)(checks: _*)

  private def testEffect[T](effect: T => Unit, probes: Boolean = true)(messages: T*)(
    checks: (Int, classic.ActorContext => Any)*
  ): Any =
    testWithChecks[T](
      ref => effectBehavior(ref, effect),
      probes
    )(messages: _*)(checks: _*)

  private def testWithoutEffect[T](messages: T*)(checks: (Int, classic.ActorContext => Any)*): Any =
    testEffect[T](_ => ())(messages: _*)(checks: _*)

  private def check[T](extr: ActorCellMetrics => T)(checkFunc: T => Any): classic.ActorContext => Any = ctx =>
    checkFunc(ActorCellDecorator.getMetrics(ctx).map(extr).value)

  "AkkaActorAgent" should "record mailbox time properly" in {
    val idle            = 100.milliseconds
    val messages        = 3
    val waitingMessages = messages - 1
    val expectedValue   = idle.toMillis.toDouble

    val check: classic.ActorContext => Any = ctx =>
      assertMetrics("mesmer_akka_mailbox_time") {
        case data if data.getType == MetricDataType.HISTOGRAM =>
          val points = data.getHistogramData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(ctx.self.path.toStringWithoutAddress)
            )

          points.map(_.getCount) should contain(3L)
          points.map(getBoundaryCountsWithToleration(_, expectedValue, Tolerance)) should contain(
            waitingMessages
          )
          forAtLeast(1, points.map(_.getSum))(_ should be(200.0 +- Tolerance))

      }

    testEffect[String] {
      case "idle" =>
        Thread.sleep(idle.toMillis)
      case _ =>
    }("idle", "", "")(3 -> check)

  }

  it should "record processing time properly" in {

    val processing      = 100.milliseconds
    val messages        = 3
    val workingMessages = messages - 1

    val expectedValue = processing.toMillis

    val check: classic.ActorContext => Any = ctx =>
      assertMetrics("mesmer_akka_processing_time") {
        case data if data.getType == MetricDataType.HISTOGRAM =>
          val points = data.getHistogramData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(ctx.self.path.toStringWithoutAddress)
            )

          points.map(_.getCount) should contain(3L)
          points.map(getBoundaryCountsWithToleration(_, expectedValue.toDouble, Tolerance)) should contain(
            workingMessages
          )
          forAtLeast(1, points.map(_.getSum))(_ should be(200.0 +- Tolerance))

      }

    testEffect[String] {
      case "work" =>
        Thread.sleep(processing.toMillis)
      case _ =>
    }("", "work", "work")(messages -> check)
  }

  it should "record stash operation from actors beginning" in {
    val stashActor = system.classicSystem.actorOf(ClassicStashActor.props(), createUniqueId)

    def sendMessage(count: Int): Unit =
      List.fill(count)(Message).foreach(m => stashActor ! m)

    def expectStashSize(size: Int): Unit =
      assertMetrics("mesmer_akka_stashed_total") {
        case data if data.getType == MetricDataType.LONG_SUM =>
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(stashActor.path.toStringWithoutAddress)
            )
            .toVector
          points.map(_.getValue) should contain(size)
      }

    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount)
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount * 2)
    stashActor ! Open
    expectStashSize(StashMessageCount * 2)
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount * 2)
    stashActor ! Close
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount * 3)
  }

  it should "record stash operation from actors beginning for typed actors" in {
    val stashActor      = system.systemActorOf(TypedStash(StashMessageCount * 4), "typedStashActor")
    val inspectionProbe = createTestProbe[StashSize]

    def sendMessage(count: Int): Unit =
      List.fill(count)(Message).foreach(stashActor.tell)

    def expectStashSize(size: Int): Unit =
      assertMetrics("mesmer_akka_stashed_total") {
        case data if data.getType == MetricDataType.LONG_SUM =>
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(stashActor.path.toStringWithoutAddress)
            )
            .toVector
          points.map(_.getValue) should contain(size)
      }

    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount)
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount * 2)
    stashActor ! Open
    expectStashSize(StashMessageCount * 2)
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount * 2)
    stashActor ! Close
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount * 3)
  }

  // TODO I don't see why we should support received messages anymore - this is the same information as processed messages
  it should "record the amount of received messages" ignore {
    testWithoutEffect[Unit]((), (), ())(
      (0, check(_.receivedMessages)(_.get.get() should be(0))),
      (1, check(_.receivedMessages)(_.get.get() should be(1))),
      (0, check(_.receivedMessages)(_.get.get() should be(1))),
      (2, check(_.receivedMessages)(_.get.get() should be(3)))
    )
  }

  it should "record the amount of failed messages without supervision" in {

    def expect(assert: Vector[Long] => Any)(context: classic.ActorContext): Any =
      assertMetrics("mesmer_akka_failed_total", successOnEmpty = true) {
        case data if data.getType == MetricDataType.LONG_SUM =>
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(context.self.path.toStringWithoutAddress)
            )
            .toVector
          assert(points.map(_.getValue))
      }

    testEffect[String](
      {
        case "fail" => throw new RuntimeException("I failed :(") with NoStackTrace
        case _      =>
      },
      false
    )("fail", "", "fail")(
      (0, expect(_ should be(empty))),
      (1, expect(_ should contain(1L))),
      (0, expect(_ should contain(1L))),
      (1, expect(_ should contain(1L))),
      // why zero? because akka suspend any further message processing after an unsupervisioned failure
      (1, expect(_ should contain(1L)))
    )
  }

  it should "record the amount of failed messages with supervision" in {

    def expect(assert: Vector[Long] => Any)(context: classic.ActorContext): Any =
      assertMetrics("mesmer_akka_failed_total", successOnEmpty = true) {
        case data if data.getType == MetricDataType.LONG_SUM =>
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(context.self.path.toStringWithoutAddress)
            )
            .toVector
          assert(points.map(_.getValue))
      }

    def testForStrategy(strategy: SupervisorStrategy): Any =
      testEffectWithSupervision[String](
        {
          case "fail" => throw new RuntimeException("I failed :(") with NoStackTrace
          case _      =>
        },
        strategy,
        probes = false
      )("fail", "", "fail", "fail")(
        (0, expect(_ should be(empty))),
        (1, expect(_ should contain(1L))),
        (0, expect(_ should contain(1L))),
        (1, expect(_ should contain(1L))),
        (2, expect(_ should contain(if (strategy != SupervisorStrategy.stop) 3 else 1)))
      )

    testForStrategy(SupervisorStrategy.restart)
    testForStrategy(SupervisorStrategy.resume)
    testForStrategy(SupervisorStrategy.stop)
  }

  it should "record the amount of unhandled messages" in {

    def expectedEmpty(check: Vector[LongPointData] => Any, successOnEmpty: Boolean = false)(
      context: classic.ActorContext
    ): Any =
      assertMetrics("mesmer_akka_unhandled_total", successOnEmpty) {
        case data if data.getType == MetricDataType.LONG_SUM =>
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(context.self.path.toStringWithoutAddress)
            )
            .toVector

          check(points)
      }

    testBehavior[String] {
      case "unhandled" => Behaviors.unhandled
      case _           => Behaviors.same
    }("unhandled", "unhandled", "unhandled", "other")(
      (0, expectedEmpty(_.map(_.getValue) should be(empty), successOnEmpty = true)),
      (1, expectedEmpty(_.map(_.getValue) should contain(1))),
      (0, expectedEmpty(_.map(_.getValue) should contain(1))),
      (2, expectedEmpty(_.map(_.getValue) should contain(3))),
      (1, expectedEmpty(_.map(_.getValue) should contain(3)))
    )

  }

  it should "record the amount of sent messages properly in classic akka" in {

    class Sender(receiver: classic.ActorRef) extends classic.Actor {
      def receive: Receive = { case "forward" =>
        receiver ! "forwarded"
      }
    }

    class Receiver extends classic.Actor with classic.ActorLogging {
      def receive: Receive = { case msg =>
        log.info(s"receiver: {}", msg)
      }
    }

    val classicSystem = system.classicSystem
    val receiver      = classicSystem.actorOf(classic.Props(new Receiver), createUniqueId)
    val sender        = system.classicSystem.actorOf(classic.Props(new Sender(receiver)), createUniqueId)

    def expectedSendMessages(num: Int): Any = assertMetrics("mesmer_akka_sent_total") {
      case data if data.getType == MetricDataType.LONG_SUM =>
        val points = data.getLongSumData.getPoints.asScala
          .filter(point =>
            Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
              .contains(sender.path.toStringWithoutAddress)
          )
          .toVector
        points.map(_.getValue) should contain(num)
    }

    sender ! "forward"
    expectedSendMessages(1)
    expectedSendMessages(1)
    sender ! "something else"
    expectedSendMessages(1)
    sender ! "forward"
    sender ! "forward"
    expectedSendMessages(3)

    sender ! PoisonPill
    receiver ! PoisonPill
  }

  it should "record the amount of sent messages properly in typed akka" in {

    def expectedEmpty(context: classic.ActorContext): Any = assertMetrics("mesmer_akka_sent_total") {
      case data if data.getType == MetricDataType.LONG_SUM =>
        val points = data.getLongSumData.getPoints.asScala
          .filter(point =>
            Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
              .contains(context.self.path.toStringWithoutAddress)
          )
          .toVector
        points.map(_.getValue) should be(empty)
    }

    testWithChecks(
      _ =>
        Behaviors.setup[String] { ctx =>
          val child = ctx.spawnAnonymous[Unit](Behaviors.ignore)

          Behaviors.receiveMessage {
            case "forward" =>
              child ! ()
              Behaviors.same
            case _ => Behaviors.same
          }
        },
      false
    )("forward", "", "forward", "forward")(
      (0, expectedEmpty),
      (1, expectedEmpty),
      (0, expectedEmpty),
      (2, expectedEmpty)
    )

  }

}

object AkkaActorAgentTest {

  type Fixture = TestProbe[ActorEvent]

  sealed trait Command
  final case object Open    extends Command
  final case object Close   extends Command
  final case object Message extends Command
  // replies
  final case class StashSize(stash: Option[Long])

  object ClassicStashActor {
    def props(): Props = Props(new ClassicStashActor)
  }
  class ClassicStashActor extends classic.Actor with classic.Stash with classic.ActorLogging {
    def receive: Receive = closed

    private val closed: Receive = {
      case Open =>
        unstashAll()
        context
          .become(opened)
      case Message =>
        stash()
    }

    private val opened: Receive = {
      case Close =>
        context.become(closed)
      case other => log.debug("Got message {}", other)
    }
  }

  object ClassicNoStashActor {
    def props: Props = Props(new ClassicNoStashActor)
  }

  class ClassicNoStashActor extends classic.Actor with classic.ActorLogging {
    def receive: Receive = { case message =>
      log.debug("Received message {}", message)
    }
  }

  object TypedStash {
    def apply(capacity: Int): Behavior[Command] =
      Behaviors.setup(ctx => Behaviors.withStash(capacity)(buffer => new TypedStash(ctx, buffer).closed()))
  }

  class TypedStash(ctx: ActorContext[Command], buffer: StashBuffer[Command]) {
    private def closed(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case m @ Message =>
          buffer.stash(m)
          Behaviors.same
      }
    private def open(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Close =>
          closed()
        case Message =>
          Behaviors.same
      }

  }

}
