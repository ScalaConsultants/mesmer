package io.scalac.extension.service

import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.actor.{ ActorRef, PoisonPill }
import akka.{ actor => classic }
import io.scalac.core.actorServiceKey
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.EventBus
import io.scalac.core.util.TestCase.{
  MonitorTestCaseContext,
  MonitorWithServiceTestCaseFactory,
  ProvidedActorSystemTestCaseFactory
}
import io.scalac.core.util.TestConfig
import io.scalac.extension.service.DeltaActorTree.{ Connect, Delta }
import io.scalac.extension.util.probe.ActorSystemMonitorProbe
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.collection.immutable
import scala.concurrent.duration._

class DeltaActorTreeTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with ProvidedActorSystemTestCaseFactory
    with MonitorWithServiceTestCaseFactory
    with Inside {

  val MaxBulkSize  = 10
  val BulkDuration = 1.second
  val ProbeTimeout = BulkDuration * 2

  protected val serviceKey: ServiceKey[_] = actorServiceKey

  type Context = DeltaActorTestContext
  type Monitor = ActorSystemMonitorProbe
  type Command = DeltaActorTree.Command

  protected def createMonitorBehavior(implicit
    context: Context
  ): Behavior[Command] =
    DeltaActorTree.apply(
      DeltaActorTreeConfig(BulkDuration, MaxBulkSize, MaxBulkSize),
      monitor,
      None,
      context.restart,
      context.traverser
    )

  implicit def connection(implicit context: Context): TestProbe[Delta] = context.subscriber

  protected def createMonitor(implicit system: ActorSystem[_]): Monitor = ActorSystemMonitorProbe.apply

  protected def createContextFromMonitor(monitor: ActorSystemMonitorProbe)(implicit
    system: ActorSystem[_]
  ): DeltaActorTestContext = DeltaActorTestContext(createTestProbe, monitor)

  private def publishCreated(ref: classic.ActorRef)(implicit system: ActorSystem[_]): Unit =
    EventBus(system).publishEvent(ActorCreated(ref))

  "DeltaActorTree" should "publish created refs to connected actor" in testCaseSetupContext { sut => implicit context =>
    sut ! Connect(connection.ref)

    val RefCount = MaxBulkSize
    val refs     = List.fill(RefCount)(system.systemActorOf(Behaviors.empty, createUniqueId)).map(_.toClassic)
    refs.foreach(publishCreated)

    monitor.globalProbe.receiveMessages(RefCount)

    inside(connection.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
      created should not be (empty)
      terminated should be(empty)
    }
  }

  it should "publish terminated actors to connected actor" in testCaseSetupContext { sut => implicit context =>
    sut ! Connect(connection.ref)

    val RefCount = MaxBulkSize
    val refs     = List.fill(RefCount)(system.systemActorOf(Behaviors.empty, createUniqueId)).map(_.toClassic)

    refs.foreach(publishCreated)

    monitor.globalProbe.receiveMessages(RefCount)

    inside(connection.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
      created should contain theSameElementsAs (refs)
      terminated should be(empty)
    }

    refs.foreach(_.unsafeUpcast[Any] ! PoisonPill)

    monitor.globalProbe.receiveMessages(RefCount)

    inside(connection.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
      created should be(empty)
      terminated should contain theSameElementsAs (refs)
    }
  }

  it should "keep local snapshot until actor connects" in testCaseSetupContext { sut => implicit context =>
    val RefCount = MaxBulkSize

    val refs = List.fill(RefCount)(system.systemActorOf(Behaviors.empty, createUniqueId)).map(_.toClassic)
    val (terminated, expected) = {
      val (terminatedWithIndex, expectedWithIndex) = refs.zipWithIndex
        .partition(_._2 % 2 == 0)
      (terminatedWithIndex.map(_._1), expectedWithIndex.map(_._1))
    }

    refs.foreach(publishCreated)

    monitor.globalProbe.receiveMessages(RefCount)

    terminated.foreach(_.unsafeUpcast[Any] ! PoisonPill)

    monitor.globalProbe.receiveMessages(terminated.size)

    sut ! Connect(connection.ref)

    inside(connection.receiveMessage(ProbeTimeout)) { case Delta(created, terminated) =>
      created should contain theSameElementsAs (expected)
      terminated should be(empty)
    }
  }

  it should "not publish empty deltas" in testCaseSetupContext { sut => implicit context =>
    sut ! Connect(connection.ref)

    connection.expectNoMessage(ProbeTimeout)
  }

  it should "use backoff traverser on restart" in testCaseWithSetupAndContext(_.copy(restart = true)) {
    sut => implicit context =>
      sut ! Connect(connection.ref)

      val root = context.traverser.getRootGuardian(system.toClassic)
      inside(connection.receiveMessage()) { case Delta(Seq(`root`), Seq()) =>
      }
  }

  final case class DeltaActorTestContext(
    subscriber: TestProbe[Delta],
    monitor: ActorSystemMonitorProbe,
    restart: Boolean = false,
    traverser: ActorTreeTraverser = RootOnlyTreeTraverser
  )(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[ActorSystemMonitorProbe]

  final case object RootOnlyTreeTraverser extends ActorTreeTraverser {

    override def getChildren(actor: ActorRef): immutable.Iterable[ActorRef] = Seq.empty
    override def getRootGuardian(system: classic.ActorSystem): ActorRef =
      ReflectiveActorTreeTraverser.getRootGuardian(system)
  }

}
