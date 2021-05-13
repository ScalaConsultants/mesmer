package io.scalac.mesmer.agent.akka.actor

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ ActorContext, Behaviors }
import akka.actor.typed.{ ActorRef, Behavior, MailboxSelector }
import com.typesafe.config.{ Config, ConfigFactory }
import io.scalac.mesmer.agent.utils.{ InstallAgent, SafeLoadSystem }
import io.scalac.mesmer.core.config.AkkaPatienceConfig
import io.scalac.mesmer.core.util.TestOps
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class ActorMailboxTest
    extends InstallAgent
    with SafeLoadSystem
    with AnyFlatSpecLike
    with Matchers
    with TestOps
    with Eventually
    with AkkaPatienceConfig {

  override def config: Config = {

    val mailboxConfig = ConfigFactory.parseString("""
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
                                                    |""".stripMargin)
    mailboxConfig.withFallback(super.config)
  }

  def publishActorContext(ref: ActorRef[ActorContext[_]]): Behavior[Unit] = Behaviors.setup { ctx =>
    ref ! ctx
    Behaviors.ignore
  }

  "ActorMailboxTest" should "publish to dropped messes for bounded node queue" in {
    val probe = createTestProbe[ActorContext[_]]

    val props = MailboxSelector
      .fromConfig("bounded-node-queue ")
      .withDispatcherFromConfig("single-thread-dispatcher")

    val sut = system.systemActorOf(publishActorContext(probe.ref), "sut", props)

    val context = probe.receiveMessage()

    system.systemActorOf(
      Behaviors.setup[Any] { _ =>
        for {
          _ <- 0 until 10
        } sut ! ()

        Behaviors.stopped[Any]
      },
      createUniqueId,
      props
    )

    eventually {
      val metrics = ActorCellDecorator.get(context.toClassic).get
      metrics.droppedMessages.map(_.get()) should be(Some(5L))
    }
  }

}
