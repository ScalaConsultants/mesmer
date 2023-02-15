package io.scalac.mesmer.instrumentation.akka.actor.otel

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import akka.actor.{ PoisonPill, Props }
import akka.{ actor => classic }
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.MetricData
import io.scalac.mesmer.agent.utils.{ OtelAgentTest, SafeLoadSystem }
import io.scalac.mesmer.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import io.scalac.mesmer.core.akka.model.AttributeNames
import io.scalac.mesmer.core.event.ActorEvent
import io.scalac.mesmer.core.util.ReceptionistOps
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

  override protected def beforeEach(): Unit =
    super.beforeEach()

  import AkkaActorAgentTest._

  private final val StashMessageCount = 10

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
    testWithChecks(_ => Behaviors.receiveMessage[T](behavior), probes = false)(messages: _*)(checks: _*)

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

  "AkkaActorAgent" should "record actors created total count" in {
    system.systemActorOf(Behaviors.ignore, createUniqueId)

    assertMetric("mesmer_akka_actor_actors_created_total") { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map {
        _.getValue
      }.sum
      totalCount should be > 0L
    }
  }

  "AkkaActorAgent" should "record actors terminated total count" in {
    system.systemActorOf(Behaviors.stopped, createUniqueId)

    assertMetric("mesmer_akka_actor_actors_terminated_total") { data =>
      val totalCount = data.getLongSumData.getPoints.asScala.map {
        _.getValue
      }.sum
      totalCount should be > 0L
    }
  }

  "AkkaActorAgent" should "record mailbox time properly" in {
    val idle            = 100.milliseconds
    val messages        = 3
    val waitingMessages = messages - 1
    val expectedValue   = idle.toMillis.toDouble

    val check: classic.ActorContext => Any = ctx =>
      assertMetric("mesmer_akka_actor_mailbox_time") { data =>
        val points = data.getHistogramData.getPoints.asScala
          .filter(point =>
            Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
              .contains(ctx.self.path.toStringWithoutAddress)
          )

        points.map(_.getCount) should contain(3L)
        points.map(getExpectedCountWithToleration(_, expectedValue, Tolerance)) should contain(
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
      assertMetric("mesmer_akka_actor_message_processing_time") { data =>
        val points = data.getHistogramData.getPoints.asScala
          .filter(point =>
            Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
              .contains(ctx.self.path.toStringWithoutAddress)
          )

        points.map(_.getCount) should contain(3L)
        points.map(getExpectedCountWithToleration(_, expectedValue.toDouble, Tolerance)) should contain(
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

    def sendMessage(count: Int): Unit = List.fill(count)(Message).foreach(m => stashActor ! m)

    def expectStashSize(size: Int): Unit =
      assertMetric("mesmer_akka_actor_stashed_messages_total") { data: MetricData =>
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
    stashActor ! Open
    expectStashSize(StashMessageCount)
    stashActor ! Close
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount)
  }

  it should "record stash operation from actors beginning for typed actors" in {
    val stashActor = system.systemActorOf(TypedStash(StashMessageCount * 4), "typedStashActor")

    def sendMessage(count: Int): Unit =
      List.fill(count)(Message).foreach(stashActor.tell)

    def expectStashSize(size: Int): Unit =
      assertMetric("mesmer_akka_actor_stashed_messages_total") { data =>
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
    stashActor ! Open
    expectStashSize(StashMessageCount)
    stashActor ! Close
    sendMessage(StashMessageCount)
    expectStashSize(StashMessageCount)
  }

  it should "record the amount of failed messages without supervision" in {

    def expect(assert: Vector[Long] => Any)(context: classic.ActorContext): Any =
      assertMetric("mesmer_akka_actor_failed_messages_total") { data =>
        // if no data is found it's OK too
        if (!data.isEmpty) {
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(context.self.path.toStringWithoutAddress)
            )
            .toVector
          assert(points.map(_.getValue))
        }
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
      assertMetric("mesmer_akka_actor_failed_messages_total") { data =>
        // if no data is found it's OK too
        if (!data.isEmpty) {
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(context.self.path.toStringWithoutAddress)
            )
            .toVector
          assert(points.map(_.getValue))
        }
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
        (2, expect(_ should contain(1L)))
      )

    testForStrategy(SupervisorStrategy.restart)
    testForStrategy(SupervisorStrategy.resume)
    testForStrategy(SupervisorStrategy.stop)
  }

  it should "record the amount of unhandled messages" ignore {

    def expectEmpty(
      context: classic.ActorContext
    ): Any =
      assertMetric("mesmer_akka_actor_unhandled_messages_total") { data =>
        if (!data.isEmpty) {
          val points = data.getLongSumData.getPoints.asScala
            .filter(point =>
              Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
                .contains(context.self.path.toStringWithoutAddress)
            )
            .toVector

          points should be(empty)
        }
      }

    def expectNum(num: Int)(
      context: classic.ActorContext
    ): Any =
      assertMetric("mesmer_akka_actor_unhandled_messages_total") { data =>
        val points = data.getLongSumData.getPoints.asScala
          .filter(point =>
            Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
              .contains(context.self.path.toStringWithoutAddress)
          )
          .toVector

        points.map(_.getValue) should contain(num)
      }

    testBehavior[String] {
      case "unhandled" => Behaviors.unhandled
      case _           => Behaviors.same
    }("unhandled", "unhandled", "unhandled", "other")(
      (0, expectEmpty),
      (1, expectNum(1)),
      (0, expectNum(1)),
      (2, expectNum(2)),
      (1, expectNum(2))
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

    def expectedSendMessages(num: Int): Any = assertMetric("mesmer_akka_actor_sent_messages_total") { data =>
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
    sender ! "something else"
    expectedSendMessages(1)

    sender ! PoisonPill
    receiver ! PoisonPill
  }

  it should "record the amount of sent messages properly in typed akka" in {

    def expectedEmpty(context: classic.ActorContext): Any = assertMetric("mesmer_akka_actor_sent_messages_total") {
      data =>
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
      probes = false
    )("forward", "", "forward", "forward")(
      (0, expectedEmpty),
      (1, expectedEmpty),
      (0, expectedEmpty),
      (1, expectedEmpty)
    )

  }

}

object AkkaActorAgentTest {

  type Fixture = TestProbe[ActorEvent]

  sealed trait Command

  final case object Open extends Command

  final case object Close extends Command

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
