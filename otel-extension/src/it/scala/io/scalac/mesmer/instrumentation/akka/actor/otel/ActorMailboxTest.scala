package io.scalac.mesmer.instrumentation.akka.actor.otel

import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior, MailboxSelector, Props }
import akka.actor.{ ActorSystem, PoisonPill }
import akka.dispatch.{ BoundedPriorityMailbox, BoundedStablePriorityMailbox, Envelope }
import com.typesafe.config.{ Config, ConfigFactory }
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.scalac.mesmer.agent.utils.{ OtelAgentTest, SafeLoadSystem }
import io.scalac.mesmer.core.akka.model.AttributeNames
import io.scalac.mesmer.core.config.AkkaPatienceConfig
import io.scalac.mesmer.core.util.TestOps
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.{ AnyFlatSpec, AnyFlatSpecLike }
import org.scalatest.matchers.should.Matchers

import java.util.Comparator
import scala.annotation.unused
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

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
    extends AnyFlatSpec
    with OtelAgentTest
    with SafeLoadSystem
    with AnyFlatSpecLike
    with Matchers
    with TestOps
    with Eventually
    with AkkaPatienceConfig {

  override def config: Config = {

    val mailboxConfig =
      ConfigFactory.parseString(
        """
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
          |  mailbox-capacity = 3
          |}
          |
          |bounded-queue {
          |  mailbox-type = "akka.dispatch.BoundedMailbox"
          |   mailbox-push-timeout-time=0
          |  mailbox-capacity = 3
          |}
          |
          |bounded-priority-queue {
          |  mailbox-type = "io.scalac.mesmer.instrumentation.akka.actor.otel.HashCodePriorityMailbox"
          |  mailbox-push-timeout-time=1
          |  mailbox-capacity = 3
          |}
          |
          |bounded-stable-priority-queue {
          |  mailbox-type = "io.scalac.mesmer.instrumentation.akka.actor.otel.StableHashCodePriorityMailbox"
          |  mailbox-push-timeout-time=1
          |  mailbox-capacity = 3
          |}
          |""".stripMargin
      )
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

  private def testWithProps(props: Props, amountOfMessages: Int = 10, expectedValue: Long = 7): Any = {

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

    assertMetrics("mesmer_akka_dropped_total") {
      case data if data.getType == MetricDataType.LONG_SUM =>
        val points = data.getLongSumData.getPoints.asScala
          .filter(point =>
            Option(point.getAttributes.get(AttributeKey.stringKey(AttributeNames.ActorPath)))
              .contains(context.self.path.toStringWithoutAddress)
          )
          .toVector

        points.map(_.getValue) should contain(expectedValue)
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
}
