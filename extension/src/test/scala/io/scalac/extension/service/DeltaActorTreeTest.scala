package io.scalac.extension.service

import akka.actor.PoisonPill
import akka.{actor => classic}
import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorSystem, Behavior}
import io.scalac.core.actorServiceKey
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.EventBus
import io.scalac.core.util.TestCase.{MonitorTestCaseContext, MonitorWithServiceTestCaseFactory, ProvidedActorSystemTestCaseFactory}
import io.scalac.core.util.TestConfig
import io.scalac.extension.service.DeltaActorTree.{ActorTerminated, Delta, Subscribe}
import io.scalac.extension.util.probe.ActorSystemMonitorProbe
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class DeltaActorTreeTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with ProvidedActorSystemTestCaseFactory
    with MonitorWithServiceTestCaseFactory
    with Inside {

  val BulkDuration = 1.second
  val ProbeTimeout = BulkDuration * 2

  protected val serviceKey: ServiceKey[_] = actorServiceKey

  type Context = DeltaActorTestContext
  type Monitor = ActorSystemMonitorProbe
  type Command = DeltaActorTree.Command

  protected def createMonitorBehavior(implicit
    context: Context
  ): Behavior[Command] =
    DeltaActorTree.apply(DeltaActorTreeConfig(BulkDuration), monitor, None)

  implicit def subscriber(implicit context: Context): TestProbe[Delta] = context.subscriber

  protected def createMonitor(implicit system: ActorSystem[_]): Monitor = ActorSystemMonitorProbe.apply

  protected def createContextFromMonitor(monitor: ActorSystemMonitorProbe)(implicit
    system: ActorSystem[_]
  ): DeltaActorTestContext = DeltaActorTestContext(createTestProbe, monitor)

  private def publishCreated(ref: classic.ActorRef)(implicit system: ActorSystem[_]): Unit = {
    EventBus(system).publishEvent(ActorCreated(ref))
  }


  "DeltaActorTree" should "current actor structure for new subsriber" in testCaseSetupContext {
    sut => implicit context =>

      val RefCount = 10
      val refs = List.fill(RefCount)(system.systemActorOf(Behaviors.empty, createUniqueId)).map(_.toClassic)
      refs.foreach(publishCreated)

      monitor.globalProbe.receiveMessages(RefCount)
      sut ! Subscribe(subscriber.ref)

      inside(subscriber.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
        created should not be (empty)
        terminated should be(empty)
      }
  }

  it should "publish actor events for subscribers" in testCaseSetupContext { sut => implicit context =>

    sut ! Subscribe(subscriber.ref)

    val RefCount = 10
    val refs = List.fill(RefCount)(system.systemActorOf(Behaviors.empty, createUniqueId)).map(_.toClassic)

    refs.foreach(publishCreated)

    monitor.globalProbe.receiveMessages(RefCount)

    inside(subscriber.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
      created should contain theSameElementsAs (refs)
      terminated should be(empty)
    }

    refs.foreach(_.unsafeUpcast[Any] ! PoisonPill)

    monitor.globalProbe.receiveMessages(RefCount)

    inside(subscriber.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
      created should be(empty)
      terminated should contain theSameElementsAs (refs)
    }
  }

  final case class DeltaActorTestContext(subscriber: TestProbe[Delta], monitor: ActorSystemMonitorProbe)(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[ActorSystemMonitorProbe]
}
