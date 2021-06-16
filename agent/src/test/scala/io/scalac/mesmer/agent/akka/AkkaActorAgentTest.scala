package io.scalac.mesmer.agent.akka

import akka.actor.PoisonPill
import akka.actor.Props
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.StashBuffer
import akka.actor.typed.scaladsl.adapter._
import akka.{ actor => classic }
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import io.scalac.mesmer.agent.utils.InstallAgent
import io.scalac.mesmer.agent.utils.SafeLoadSystem
import io.scalac.mesmer.core.event.ActorEvent
import io.scalac.mesmer.core.util.MetricsToolKit.Counter
import io.scalac.mesmer.core.util.ReceptionistOps
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import io.scalac.mesmer.extension.actor.ActorCellMetrics

class AkkaActorAgentTest
    extends InstallAgent
    with AnyFlatSpecLike
    with ReceptionistOps
    with OptionValues
    with Eventually
    with Matchers
    with SafeLoadSystem {

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

  private def check[T](extr: ActorCellMetrics => T)(checkFunc: T => Any): classic.ActorContext => Any = ctx => {
    checkFunc(ActorCellDecorator.get(ctx).map(extr).value)
  }

  "AkkaActorAgent" should "record mailbox time properly" in {
    val idle            = 100.milliseconds
    val messages        = 3
    val waitingMessages = messages - 1

    val check: classic.ActorContext => Any = ctx => {
      val metrics = ActorCellDecorator.get(ctx).flatMap(_.mailboxTimeAgg.metrics).value
      metrics.count should be(messages)
      metrics.sum should be((waitingMessages * idle.toNanos) +- ToleranceNanos)
      metrics.min should be(0L +- ToleranceNanos)
      metrics.max should be(idle.toNanos +- ToleranceNanos)
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

    val check: classic.ActorContext => Any = ctx => {
      val metrics = ActorCellDecorator.get(ctx).flatMap(_.processingTimeAgg.metrics).value
      metrics.count should be(messages)
      metrics.sum should be((workingMessages * processing.toNanos) +- ToleranceNanos)
      metrics.min should be(0L +- ToleranceNanos)
      metrics.max should be(processing.toNanos +- ToleranceNanos)
    }

    testEffect[String] {
      case "work" =>
        Thread.sleep(processing.toMillis)
      case _ =>
    }("", "work", "work")(3 -> check)
  }

  it should "record stash operation from actors beginning" in {
    val stashActor      = system.classicSystem.actorOf(ClassicStashActor.props(), createUniqueId)
    val inspectionProbe = createTestProbe[StashSize]

    def sendMessage(count: Int): Unit =
      List.fill(count)(Message).foreach(m => stashActor ! m)

    def expectStashSize(size: Int): Unit = {
      stashActor ! Inspect(inspectionProbe.ref.toClassic)
      inspectionProbe.expectMessage(StashSize(Some(size)))
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

  it should "return no stash data for actors without stash" in {
    val sut             = system.classicSystem.actorOf(ClassicNoStashActor.props, createUniqueId)
    val inspectionProbe = createTestProbe[StashSize]

    List.fill(StashMessageCount)(Message).foreach(m => sut ! m)

    sut ! Inspect(inspectionProbe.ref.toClassic)
    inspectionProbe.expectMessage(StashSize(None))
  }

  it should "record stash operation from actors beginning for typed actors" in {
    val stashActor      = system.systemActorOf(TypedStash(StashMessageCount * 4), "typedStashActor")
    val inspectionProbe = createTestProbe[StashSize]

    def sendMessage(count: Int): Unit =
      List.fill(count)(Message).foreach(stashActor.tell)

    def expectStashSize(number: Int): Unit = {
      stashActor ! Inspect(inspectionProbe.ref.toClassic)
      inspectionProbe.expectMessage(StashSize(Some(number)))
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

  it should "record the amount of received messages" in {
    testWithoutEffect[Unit]((), (), ())(
      (0, check(_.receivedMessages)(_.get() should be(0))),
      (1, check(_.receivedMessages)(_.get() should be(1))),
      (0, check(_.receivedMessages)(_.get() should be(1))),
      (2, check(_.receivedMessages)(_.get() should be(3)))
    )
  }

  it should "record the amount of failed messages without supervision" in {

    testEffect[String](
      {
        case "fail" => throw new RuntimeException("I failed :(") with NoStackTrace
        case _      =>
      },
      false
    )("fail", "", "fail")(
      (0, check(_.failedMessages)(_.get() should be(0))),
      (1, check(_.failedMessages)(_.get() should be(1))),
      (0, check(_.failedMessages)(_.get() should be(1))),
      (1, check(_.failedMessages)(_.get() should be(1))),
      // why zero? because akka suspend any further message processing after an unsupervisioned failure
      (1, check(_.failedMessages)(_.get() should be(1)))
    )
  }

  it should "record the amount of failed messages with supervision" in {

    def testForStrategy(strategy: SupervisorStrategy): Any =
      testEffectWithSupervision[String](
        {
          case "fail" => throw new RuntimeException("I failed :(") with NoStackTrace
          case _      =>
        },
        strategy,
        probes = false
      )("fail", "", "fail", "fail")(
        (0, check(_.failedMessages)(_.get() should be(0))),
        (1, check(_.failedMessages)(_.get() should be(1))),
        (0, check(_.failedMessages)(_.get() should be(1))),
        (1, check(_.failedMessages)(_.get() should be(1))),
        (2, check(_.failedMessages)(_.get() should be(if (strategy != SupervisorStrategy.stop) 3 else 1)))
      )

    testForStrategy(SupervisorStrategy.restart)
    testForStrategy(SupervisorStrategy.resume)
    testForStrategy(SupervisorStrategy.stop)
  }

  it should "record the amount of unhandled messages" in {

    testBehavior[String] {
      case "unhandled" => Behaviors.unhandled
      case _           => Behaviors.same
    }("unhandled", "unhandled", "unhandled", "other")(
      (0, check(_.unhandledMessages)(_.get() should be(0))),
      (1, check(_.unhandledMessages)(_.get() should be(1))),
      (0, check(_.unhandledMessages)(_.get() should be(1))),
      (2, check(_.unhandledMessages)(_.get() should be(3))),
      (1, check(_.unhandledMessages)(_.get() should be(3)))
    )

  }

  it should "record the amount of sent messages properly in classic akka" in {

    var senderContext: Option[classic.ActorContext] = None
    class Sender(receiver: classic.ActorRef) extends classic.Actor {
      senderContext = Some(context)
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

    val sent = createCounterChecker(senderContext.get, _.sentMessages)

    sender ! "forward"
    sent(1)
    sent(0)
    sender ! "something else"
    sent(0)
    sender ! "forward"
    sender ! "forward"
    sent(2)
    sent(0)

    sender ! PoisonPill
    receiver ! PoisonPill
  }

  it should "record the amount of sent messages properly in typed akka" in {

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
      (0, check(_.sentMessages)(_.get() should be(0))),
      (1, check(_.sentMessages)(_.get() should be(0))),
      (0, check(_.sentMessages)(_.get() should be(0))),
      (2, check(_.sentMessages)(_.get() should be(0)))
    )

  }

  private def createCounterChecker[T](
    ctx: => classic.ActorContext,
    metricProvider: ActorCellMetrics => Counter
  ): Long => Unit = {
    val metric = eventually {
      metricProvider(ActorCellDecorator.get(ctx).value)
    }
    test =>
      eventually {
        metric.get() should be(test)
      }
      metric.reset()
  }

}

object AkkaActorAgentTest {

  type Fixture = TestProbe[ActorEvent]

  sealed trait Command
  final case object Open                              extends Command
  final case object Close                             extends Command
  final case object Message                           extends Command
  final case class Inspect(replyTo: classic.ActorRef) extends Command
  //replies
  final case class StashSize(stash: Option[Long])

  trait Inspectable {

    protected def inspectStashSize(ref: classic.ActorRef, ctx: classic.ActorContext): Unit = {
      val size = ActorCellDecorator.get(ctx).flatMap(_.stashSize.get())
      ref ! StashSize(size)
    }
  }

  object ClassicStashActor {
    def props(): Props = Props(new ClassicStashActor)
  }
  class ClassicStashActor extends classic.Actor with classic.Stash with classic.ActorLogging with Inspectable {
    def receive: Receive = closed

    private val closed: Receive = {
      case Open =>
        unstashAll()
        context
          .become(opened)
      case Message =>
        stash()
      case Inspect(ref) =>
        inspectStashSize(ref, context)
    }

    private val opened: Receive = {
      case Close =>
        context.become(closed)
      case Inspect(ref) =>
        inspectStashSize(ref, context)
      case other => log.debug("Got message {}", other)
    }
  }

  object ClassicNoStashActor {
    def props: Props = Props(new ClassicNoStashActor)
  }

  class ClassicNoStashActor extends classic.Actor with classic.ActorLogging with Inspectable {
    def receive: Receive = {
      case Inspect(ref) => inspectStashSize(ref, context)
      case message =>
        log.debug("Received message {}", message)
    }
  }

  object TypedStash {
    def apply(capacity: Int): Behavior[Command] =
      Behaviors.setup(ctx => Behaviors.withStash(capacity)(buffer => new TypedStash(ctx, buffer).closed()))
  }

  class TypedStash(ctx: ActorContext[Command], buffer: StashBuffer[Command]) extends Inspectable {
    private def closed(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Open =>
          buffer.unstashAll(open())
        case m @ Message =>
          buffer.stash(m)
          Behaviors.same
        case Inspect(ref) =>
          inspectStashSize(ref, ctx.toClassic)
          Behaviors.same
      }
    private def open(): Behavior[Command] =
      Behaviors.receiveMessagePartial {
        case Close =>
          closed()
        case Message =>
          Behaviors.same
        case Inspect(ref) =>
          inspectStashSize(ref, ctx.toClassic)
          Behaviors.same
      }

  }

}
