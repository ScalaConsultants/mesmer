package io.scalac.extension.service

import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorSystem, Behavior }
import akka.{ actor => classic }
import io.scalac.core.model.Tag
import io.scalac.core.util.TestCase.{
  MonitorWithActorRefSetupTestCaseFactory,
  MonitorWithBasicContextTestCaseFactory,
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
    with MonitorWithBasicContextTestCaseFactory
    with Inside {

  override type Command = ActorTreeService.Command

  override protected def createMonitorBehavior(implicit context: Context): Behavior[Command] = ActorTreeService(
    TestBehaviors.Pass.toRef(monitor.ref)
  )

  override type Monitor = TestProbe[DeltaActorTree.Command]

  override protected def createMonitor(implicit system: ActorSystem[_]): Monitor = createTestProbe()

  "ActorTreeServiceTest" should "connect to DeltaActorTree" in testCaseSetupContext { sut => implicit context =>
    val message = monitor.expectMessageType[Connect]
    message.ref should sameOrParent(sut)
  }

  //TODO simplify test data creation
  it should "store local actor tree snapshot" in testCaseSetupContext { sut => implicit context =>
    val Connect(ref)                     = monitor.expectMessageType[Connect]
    val frontTestProbe                   = createTestProbe[Seq[classic.ActorRef]]()
    val CreatedCount                     = 10
    val createdRefs                      = List.fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
    val (terminatedRefs, expectedResult) = createdRefs.splitAt(CreatedCount / 2)

    val createdDeltas = createdRefs.map(ref => Delta(created = Seq(ref), terminated = Seq.empty))

    val terminatedDeltas = terminatedRefs.map(ref => Delta(terminated = Seq(ref), created = Seq.empty))

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

      frontTestProbe.receiveMessage() should contain theSameElementsAs (expectedResult)
    }

  }

}
