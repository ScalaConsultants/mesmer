package io.scalac.extension

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors

import org.scalatest.Inspectors
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.extension.ActorEventsMonitorActor.{ ActorTreeTraverser, ReflectiveActorTreeTraverser }
import io.scalac.extension.actor.MutableActorMetricsStorage
import io.scalac.extension.metric.ActorMetricMonitor
import io.scalac.extension.util.probe.ActorMonitorTestProbe
import io.scalac.extension.util.probe.ActorMonitorTestProbe.TestBoundMonitor
import io.scalac.extension.util.probe.BoundTestProbe.MetricObserved

class ActorEventsMonitorActorTest extends AnyFlatSpecLike with Matchers with Inspectors {

  testActorTreeRunner(ReflectiveActorTreeTraverser)
  testMonitor()

  def testActorTreeRunner(actorTreeRunner: ActorTreeTraverser): Unit = {
    val system = createActorSystem()

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
      Thread.sleep(100) // waiting system...
      val root             = actorTreeRunner.getRootGuardian(system.classicSystem)
      val children         = actorTreeRunner.getChildren(root)
      val guardian         = children.find(_.path.toStringWithoutAddress == "/user").get
      val guardianChildren = actorTreeRunner.getChildren(guardian)
      guardianChildren.map(_.path.toStringWithoutAddress) should contain theSameElementsAs (Set(
        "/user/actorA",
        "/user/actorB"
      ))
      system.terminate()
      Await.ready(system.whenTerminated, 5.seconds)
    }

    it should "terminate actorSystem" in {
      system.terminate()
      Await.ready(system.whenTerminated, 5.seconds)
    }

  }

  def testMonitor(): Unit = {

    val pingOffset = 2.seconds

    def test(block: (ActorMonitorTestProbe, ActorSystem[String]) => Unit): Unit = {
      implicit val system: ActorSystem[String] = createActorSystem()

      val monitor = new ActorMonitorTestProbe()

      system.systemActorOf(
        ActorEventsMonitorActor(monitor, None, pingOffset, MutableActorMetricsStorage.empty),
        "actorEventsMonitorActor"
      )
      block(monitor, system)
      system.terminate()
      Await.ready(system.whenTerminated, 2.seconds)
    }

    "ActorEventsMonitor" should "record mailbox size" in test { (monitor, system) =>
      val bound = monitor.bind(ActorMetricMonitor.Labels("/user/actorB/idle", None))
      recordMailboxSize(10, bound, system)
      bound.unbind()
    }

    it should "record mailbox size changes" in test { (monitor, system) =>
      val bound = monitor.bind(ActorMetricMonitor.Labels("/user/actorB/idle", None))
      recordMailboxSize(10, bound, system)
      Thread.sleep(1000)
      recordMailboxSize(42, bound, system)
      bound.unbind()
    }

    def recordMailboxSize(n: Int, bound: TestBoundMonitor, system: ActorSystem[String]): Unit = {
      system ! "idle"
      for (_ <- 0 until n) system ! "Record it"
      val records = bound.mailboxSizeProbe.fishForMessage(3 * pingOffset) {
        case MetricObserved(`n`) => FishingOutcome.Complete
        case _                   => FishingOutcome.ContinueAndIgnore
      }
      records.size should not be (0)
    }

  }

  private def createActorSystem(): ActorSystem[String] =
    ActorSystem[String](
      Behaviors.setup[String] { ctx =>
        val actorA = ctx.spawn[String](
          Behaviors.setup[String] { ctx =>
            import ctx.log

            val actorAA = ctx.spawn[String](Behaviors.setup { ctx =>
              import ctx.log
              Behaviors.receiveMessage { msg =>
                log.info("[actorAA] received a message: {}", msg)
                Behaviors.same
              }
            }, "actorAA")

            Behaviors.receiveMessage { msg =>
              log.info("[actorA] received a message: {}", msg)
              actorAA ! msg
              Behaviors.same
            }
          },
          "actorA"
        )

        val actorB = ctx.spawn[String](
          Behaviors.setup { ctx =>
            import ctx.log

            val actorBIdle = ctx.spawn[String](
              Behaviors.setup { ctx =>
                import ctx.log
                Behaviors.receiveMessage {
                  case "idle" =>
                    log.info("[idle] ...")
                    Thread.sleep(5.seconds.toMillis)
                    Behaviors.same
                  case _ =>
                    Behaviors.same
                }
              },
              "idle"
            )

            Behaviors.receiveMessage { msg =>
              log.info("[actorB] received a message: {}", msg)
              actorBIdle ! msg
              Behaviors.same
            }
          },
          "actorB"
        )

        Behaviors.receiveMessage { msg =>
          actorA ! msg
          actorB ! msg
          Behaviors.same
        }
      },
      "actorEventsMonitorActorTest"
    )

}
