package io.scalac.extension.service

import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import akka.{ actor => classic }
import io.scalac.core.model.{ ActorRefDetails, Tag }
import io.scalac.core.util.TestCase.{
  MonitorTestCaseContext,
  MonitorWithActorRefSetupTestCaseFactory,
  ProvidedActorSystemTestCaseFactory
}
import io.scalac.core.util.{ TestBehaviors, TestConfig }
import io.scalac.extension.service.ActorTreeService.GetActors
import io.scalac.extension.service.DeltaActorTree.{ Connect, Delta }
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.util.Random

class ActorTreeServiceTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with ProvidedActorSystemTestCaseFactory
    with MonitorWithActorRefSetupTestCaseFactory
    with Inside {

  type Command = ActorTreeService.Command
  type Monitor = TestProbe[DeltaActorTree.Command]
  type Context = ActorTreeServiceTestContext

  protected def createContextFromMonitor(monitor: TestProbe[DeltaActorTree.Command])(implicit
    system: ActorSystem[_]
  ): Context = ActorTreeServiceTestContext(monitor)

  protected def createMonitorBehavior(implicit context: Context): Behavior[Command] = {
    val deltaTreeBehaviorProducer: Boolean => Behavior[DeltaActorTree.Command] =
      if (context.failing) failThenPass(monitor.ref) else _ => TestBehaviors.Pass.toRef(monitor.ref)
    ActorTreeService(
      deltaTreeBehaviorProducer
    )
  }

  private def failThenPass(
    ref: ActorRef[DeltaActorTree.Command]
  )(restarted: Boolean): Behavior[DeltaActorTree.Command] = if (restarted) {
    TestBehaviors.Pass.toRef(ref)
  } else TestBehaviors.Failing()

  override protected def createMonitor(implicit system: ActorSystem[_]): Monitor = createTestProbe()

  "ActorTreeServiceTest" should "connect to DeltaActo`rTree" in testCaseSetupContext { sut => implicit context =>
    val message = monitor.expectMessageType[Connect]
    message.ref should sameOrParent(sut)
  }

  //TODO simplify test data creation
  it should "store local actor tree snapshot" in testCaseSetupContext { sut => implicit context =>
    val Connect(ref)   = monitor.expectMessageType[Connect]
    val frontTestProbe = createTestProbe[Seq[classic.ActorRef]]()
    val CreatedCount   = 10
    val createdRefs = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set.empty))
    val (terminatedRefs, expectedResult) = createdRefs.splitAt(CreatedCount / 2)

    val createdDeltas = createdRefs.map(details => Delta(created = Seq(details), terminated = Seq.empty))

    val terminatedDeltas = terminatedRefs.map(details => Delta(terminated = Seq(details.ref), created = Seq.empty))

    val deltas = (createdDeltas.take(terminatedDeltas.size) ++ Random
      .shuffle(
        (createdDeltas.drop(terminatedDeltas.size) ++ terminatedDeltas)
      ))
      .grouped(CreatedCount / 2)
      .map(_.reduceLeft[Delta] { case (left, next) =>
        Delta(created = left.created ++ next.created, terminated = left.terminated ++ next.terminated)
      })

    deltas.foreach(ref.tell)

    eventually {
      sut ! GetActors(Tag.all, frontTestProbe.ref)

      frontTestProbe.receiveMessage() should contain theSameElementsAs (expectedResult.map(_.ref))
    }
  }

  it should "recreate DeltaActorTree on failure with restart flag set to true" in testCaseWith(
    _.copy(failing = true)
  ) { implicit context =>
    monitor.expectMessageType[Connect]
  }

  final case class ActorTreeServiceTestContext(monitor: TestProbe[DeltaActorTree.Command], failing: Boolean = false)(
    implicit val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[Monitor]

}
