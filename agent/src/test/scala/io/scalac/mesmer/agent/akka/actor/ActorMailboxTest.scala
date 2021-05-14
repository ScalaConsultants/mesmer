package io.scalac.mesmer.agent.akka.actor

import java.util.Comparator

import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.MailboxSelector
import akka.actor.typed.Props
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.dispatch.BoundedPriorityMailbox
import akka.dispatch.BoundedStablePriorityMailbox
import akka.dispatch.Envelope
import akka.{ actor => classic }
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.annotation.unused
import scala.concurrent.duration.Duration
import scala.jdk.DurationConverters._

import io.scalac.mesmer.agent.akka.actor.ActorMailboxTest.ClassicContextPublish
import io.scalac.mesmer.agent.utils.InstallAgent
import io.scalac.mesmer.agent.utils.SafeLoadSystem
import io.scalac.mesmer.core.config.AkkaPatienceConfig
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import io.scalac.mesmer.extension.actor.ActorCellMetrics
import io.scalac.mesmer.extension.actor.DroppedMessagesCellMetrics

final class HashCodePriorityMailbox(
  capacity: Int,
  pushTimeOut: Duration
) extends BoundedPriorityMailbox(
      Comparator.comparingInt[Envelope](_.hashCode),
      capacity,
      pushTimeOut
    ) {
  def this(@unused settings: ActorSystem.Settings, config: Config) =
    this(config.getInt("mailbox-capacity"), config.getDuration("mailbox-push-timeout-time").toScala)
}

final class StableHashCodePriorityMailbox(
  capacity: Int,
  pushTimeOut: Duration
) extends BoundedStablePriorityMailbox(
      Comparator.comparingInt[Envelope](_.hashCode),
      capacity,
      pushTimeOut
    ) {
  def this(@unused settings: ActorSystem.Settings, config: Config) =
    this(config.getInt("mailbox-capacity"), config.getDuration("mailbox-push-timeout-time").toScala)
}

class ActorMailboxTest
    extends InstallAgent
    with SafeLoadSystem
    with AnyFlatSpecLike
    with Matchers
    with TestOps
    with Eventually
    with AkkaPatienceConfig {

  override def config: Config = {

    val mailboxConfig =
      ConfigFactory.parseString("""
                                  |single-thread-dispatcher {
                                  |  type = Dispatcher
                                  |  executor = "thread-pool-executor"
                                  |
                                  |  thread-pool-executor {
                                  |    fixed-pool-size = 1
                                  |  }
                                  |  throughput = 10
                                  |}
                                  |
                                  |bounded-node-queue {
                                  |  mailbox-type = "akka.dispatch.NonBlockingBoundedMailbox"
                                  |  mailbox-capacity = 5
                                  |}
                                  |
                                  |bounded-queue {
                                  |  mailbox-type = "akka.dispatch.BoundedMailbox"
                                  |   mailbox-push-timeout-time=0
                                  |  mailbox-capacity = 5
                                  |}
                                  |
                                  |bounded-priority-queue {
                                  |  mailbox-type = "io.scalac.mesmer.agent.akka.actor.HashCodePriorityMailbox"
                                  |  mailbox-push-timeout-time=1
                                  |  mailbox-capacity = 5
                                  |}
                                  |
                                  |bounded-stable-priority-queue {
                                  |  mailbox-type = "io.scalac.mesmer.agent.akka.actor.StableHashCodePriorityMailbox"
                                  |  mailbox-push-timeout-time=1
                                  |  mailbox-capacity = 5
                                  |}
                                  |""".stripMargin)
    mailboxConfig.withFallback(super.config)
  }

  def publishActorContext(ref: ActorRef[ActorContext[Unit]]): Behavior[Unit] = Behaviors.setup { ctx =>
    ref ! ctx
    Behaviors.ignore
  }

  private def actorRefWithContext(props: Props): (ActorRef[Unit], ActorContext[Unit]) = {
    val probe = createTestProbe[ActorContext[Unit]]

    val sut = system.systemActorOf(publishActorContext(probe.ref), createUniqueId, props)

    val context = probe.receiveMessage()

    (sut, context)
  }

  private def classicActorRefWithContext(props: classic.Props): (classic.ActorRef, classic.ActorContext) = {
    val probe = createTestProbe[classic.ActorContext]

    val sut = system.toClassic.actorOf(
      ClassicContextPublish.props(probe.ref).withMailbox(props.mailbox).withDispatcher(props.dispatcher)
    )

    val context = probe.receiveMessage()

    (sut, context)
  }

  private def testWithProps(props: Props, amountOfMessages: Int = 10, expectedValue: Long = 5): Any = {

    val (sut, context) = actorRefWithContext(props)

    system.systemActorOf(
      Behaviors.setup[Any] { _ =>
        for {
          _ <- 0 until amountOfMessages
        } sut ! ()

        Behaviors.stopped[Any]
      },
      createUniqueId,
      props
    )
    eventually {
      val metrics = ActorCellDecorator.get(context.toClassic).get
      metrics.droppedMessages.map(_.get()) should be(Some(expectedValue))
    }
    sut.unsafeUpcast[Any] ! PoisonPill
  }

  "ActorMailboxTest" should "increase dropped messages for bounded node queue" in {
    val props = MailboxSelector
      .fromConfig("bounded-node-queue ")
      .withDispatcherFromConfig("single-thread-dispatcher")
    testWithProps(props)
  }

  it should "increase dropped messages for bounded queue" in {
    val props = MailboxSelector
      .fromConfig("bounded-queue ")
      .withDispatcherFromConfig("single-thread-dispatcher")
    testWithProps(props)
  }

  it should "increase dropped messages for priority mailbox" in {
    val props = MailboxSelector
      .fromConfig("bounded-priority-queue")
      .withDispatcherFromConfig("single-thread-dispatcher")
    testWithProps(props)
  }

  it should "increase dropped messages for stable priority mailbox" in {
    val props = MailboxSelector
      .fromConfig("bounded-stable-priority-queue")
      .withDispatcherFromConfig("single-thread-dispatcher")
    testWithProps(props)
  }

  it should "not have dropped messages defined for default typed configuration" in {
    val (sut, context) = actorRefWithContext(Props.empty)

    val metrics = ActorCellDecorator.get(context.toClassic).get
    metrics.droppedMessages should be(None)
    assertThrows[ClassCastException] {
      metrics.asInstanceOf[ActorCellMetrics with DroppedMessagesCellMetrics]
    }
    sut.unsafeUpcast ! PoisonPill
  }

  it should "not have dropped messages defined for default classic configuration" in {
    val (sut, context) = classicActorRefWithContext(classic.Props.empty)

    val metrics = ActorCellDecorator.get(context).get
    metrics.droppedMessages should be(None)
    assertThrows[ClassCastException] {
      metrics.asInstanceOf[ActorCellMetrics with DroppedMessagesCellMetrics]
    }
    sut.unsafeUpcast ! PoisonPill
  }

}

object ActorMailboxTest {

  object ClassicContextPublish {
    def props(ref: ActorRef[classic.ActorContext]): classic.Props = classic.Props(new ClassicContextPublish(ref))
  }
  class ClassicContextPublish(ref: ActorRef[classic.ActorContext]) extends classic.Actor {
    ref ! context
    val receive: Receive = Function.const[Any, Unit](()) _
  }
}
