package io.scalac.agent.akka

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
import akka.actor.typed.{ ActorRef, Behavior, SupervisorStrategy }
import akka.actor.{ PoisonPill, Props }
import akka.{ actor => classic }
import io.scalac.agent.utils.{ InstallAgent, SafeLoadSystem }
import io.scalac.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import io.scalac.core.event.ActorEvent
import io.scalac.core.model._
import io.scalac.core.util.{ ActorPathOps, MetricsToolKit, ReceptionistOps }
import org.scalatest.OptionValues
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Millis, Span }

import scala.concurrent._
import scala.concurrent.duration._

class AkkaActorAgentTest
    extends InstallAgent
    with AnyFlatSpecLike
    with ReceptionistOps
    with OptionValues
    with Eventually
    with Matchers
    with SafeLoadSystem {

  import AkkaActorAgentTest._

  override implicit val patienceConfig: PatienceConfig = PatienceConfig().copy(scaled(Span(1000, Millis)))

  private final val StashMessageCount = 10

  "AkkaActorAgent" should "record mailbox time properly" in {
    val idle      = 100.milliseconds
    val tolerance = 100
    testWithContextAndActor[String](_ =>
      Behaviors.receiveMessage {
        case "idle" =>
          Thread.sleep(idle.toMillis)
          Behaviors.same
        case _ =>
          Behaviors.same
      }
    ) { (ctx, actor) =>
      val n       = 3
      val waiting = n - 1
      actor ! "idle"
      for (_ <- 0 until waiting) actor ! "42"
      eventually {
        val metrics = ActorCellDecorator.get(ctx).flatMap(_.mailboxTimeAgg.metrics).value
        metrics.count should be(n)
        metrics.avg should be(((waiting * idle.toMillis) / n) +- tolerance)
        metrics.sum should be((waiting * idle.toMillis) +- tolerance)
        metrics.min should be(0L +- tolerance)
        metrics.max should be(idle.toMillis +- tolerance)
      }
    }
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

  it should "record the amount of received messages" in testWithContextAndActor[String](_ => Behaviors.ignore) {
    (ctx, actor) =>
      val received = createCounterChecker(ctx, _.receivedMessages)

      received(0)
      actor ! "42"
      received(1)
      received(0)
      actor ! "42"
      actor ! "42"
      received(2)
  }

  it should "record the amount of failed messages without supervision" in testWithContextAndActor[String](_ =>
    Behaviors.receiveMessage {
      case "fail" =>
        throw new RuntimeException("I failed :(")
      case _ =>
        Behaviors.same
    }
  ) { (ctx, actor) =>
    val failed = createCounterChecker(ctx, _.failedMessages)

    failed(0)
    actor ! "fail"
    failed(1)
    failed(0)
    actor ! ":)"
    failed(0)
    actor ! "fail"
    failed(0) // why zero? because akka suspend any further message processing after an unsupervisioned failure
  }

  it should "record the amount of failed messages with supervision" in {

    def testForStrategy(strategy: SupervisorStrategy): Unit = testWithContextAndActor[String](_ =>
      Behaviors
        .supervise[String](
          Behaviors.receiveMessage {
            case "fail" =>
              throw new RuntimeException(s"[strategy = $strategy]I failed :(")
            case _ =>
              Behaviors.same
          }
        )
        .onFailure[RuntimeException](strategy)
    ) { (ctx, actor) =>
      val failed = createCounterChecker(ctx, _.failedMessages)

      failed(0)
      actor ! "fail"
      failed(1)
      failed(0)
      actor ! ":)"
      failed(0)
      actor ! "fail"
      actor ! "fail"
      if (strategy != SupervisorStrategy.stop) failed(2)
      failed(0)
    }

    testForStrategy(SupervisorStrategy.restart)
    testForStrategy(SupervisorStrategy.resume)
    testForStrategy(SupervisorStrategy.stop)
  }

  it should "record the amount of unhandled messages" in testWithContextAndActor[String](_ =>
    Behaviors.receiveMessage {
      case "receive" => Behaviors.same
      case _         => Behaviors.unhandled
    }
  ) { (ctx, actor) =>
    val unhandled = createCounterChecker(ctx, _.unhandledMessages)

    unhandled(0)
    actor ! "42"
    unhandled(1)
    unhandled(0)
    actor ! "42"
    actor ! "42"
    unhandled(2)
    actor ! "receive"
    unhandled(0)
  }

  it should "record processing time properly" in {
    val processing = 100.milliseconds
    val tolerance  = 50
    testWithContextAndActor[String](_ =>
      Behaviors.receiveMessage {
        case "work" =>
          Thread.sleep(processing.toMillis)
          Behaviors.same
        case _ =>
          Behaviors.same
      }
    ) { (ctx, actor) =>
      val n       = 3
      val working = n - 1
      actor ! "42"
      for (_ <- 0 until working) actor ! "work"
      eventually {
        val metrics = ActorCellDecorator.get(ctx).flatMap(_.processingTimeAgg.metrics).value
        metrics.count should be(n)
        metrics.avg should be(((working * processing.toMillis) / n) +- tolerance)
        metrics.sum should be((working * processing.toMillis) +- tolerance)
        metrics.min should be(0L +- tolerance)
        metrics.max should be(processing.toMillis +- tolerance)
      }
    }
  }

  it should "record the amount of sent messages properly in classic akka" in {

    var senderContext: Option[classic.ActorContext] = None
    class Sender(receiver: classic.ActorRef) extends classic.Actor {
      senderContext = Some(context)
      override def receive: Receive = { case "forward" =>
        receiver ! "forwarded"
      }
    }

    class Receiver extends classic.Actor with classic.ActorLogging {
      override def receive: Receive = { case msg =>
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

  it should "record the amount of sent messages properly in typed akka" in testWithContextAndActor[String] { ctx =>
    val receiver = ctx.spawn(
      Behaviors.receiveMessage[String] { msg =>
        println(msg)
        Behaviors.same
      },
      createUniqueId
    )
    Behaviors.receiveMessagePartial[String] { case "forward" =>
      receiver ! "forwarded"
      Behaviors.same
    }
  } { (ctx, sender) =>
    val sent = createCounterChecker(ctx, _.sentMessages)
    // Disclaimer: always 0 because isn't possible to fetch the sender of a message
    // Ref: https://doc.akka.io/docs/akka/current/typed/from-classic.html#sender
    sender ! "forward"
    sent(0)
    sent(0)
    sender ! "something else"
    sent(0)
    sender ! "forward"
    sender ! "forward"
    sent(0)
    sent(0)

    sender.unsafeUpcast[Any] ! PoisonPill
  }

  def testWithContextAndActor[T](
    behavior: ActorContext[T] => Behavior[T]
  )(
    block: (classic.ActorContext, ActorRef[T]) => Unit
  ): Unit = {
    var ctxRef: Option[classic.ActorContext] = None
    val testActor = system.systemActorOf(
      Behaviors.setup[T] { ctx =>
        ctxRef = Some(ctx.toClassic)
        behavior(ctx)
      },
      createUniqueId
    )
    Await.ready(
      Future {
        blocking {
          while (ctxRef.isEmpty)
            Thread.sleep(100)
        }
      }(ExecutionContext.global),
      2.seconds
    )
    block(ctxRef.get, testActor)
    testActor.unsafeUpcast[Any] ! PoisonPill
  }

  private def createCounterChecker[T](
    ctx: => classic.ActorContext,
    metricProvider: ActorCellMetrics => MetricsToolKit.Counter
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
    override def receive: Receive = {
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
        case m @ Message =>
          Behaviors.same
        case Inspect(ref) =>
          inspectStashSize(ref, ctx.toClassic)
          Behaviors.same
      }

  }

}
