package io.scalac.extension.service

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.{ actor => classic }
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import io.scalac.core.event.ActorEvent
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.ActorEvent.TagsSet
import io.scalac.core.model.ActorRefDetails
import io.scalac.core.model.Tag
import io.scalac.core.util.TestCase.MonitorTestCaseContext
import io.scalac.core.util.TestCase.MonitorWithActorRefSetupTestCaseFactory
import io.scalac.core.util.TestCase.ProvidedActorSystemTestCaseFactory
import io.scalac.core.util.TestConfig
import io.scalac.extension.service.ActorTreeService.Command.GetActors
import io.scalac.extension.service.ActorTreeServiceTest.EmptyActorTreeTraverser
import io.scalac.extension.util.probe.ActorSystemMonitorProbe

object ActorTreeServiceTest {
  object EmptyActorTreeTraverser extends ActorTreeTraverser {
    def getChildren(actor: classic.ActorRef): Seq[classic.ActorRef] = Seq.empty

    def getRootGuardian(system: classic.ActorSystem): classic.ActorRef =
      ReflectiveActorTreeTraverser.getRootGuardian(system)
  }
}

class ActorTreeServiceTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with ProvidedActorSystemTestCaseFactory
    with MonitorWithActorRefSetupTestCaseFactory
    with Inside {

  type Command = ActorTreeService.Api
  type Monitor = ActorSystemMonitorProbe
  type Context = ActorTreeServiceTestContext

  protected def createContextFromMonitor(monitor: Monitor)(implicit
    system: ActorSystem[_]
  ): Context = ActorTreeServiceTestContext(monitor, createTestProbe(), EmptyActorTreeTraverser)

  protected def createMonitorBehavior(implicit context: Context): Behavior[Command] =
    Behaviors.setup { ctx =>
      new ActorTreeService(ctx, monitor, ref => context.bindProbe.ref ! ref, context.traverser)
    }

  protected def createMonitor(implicit system: ActorSystem[_]): Monitor =
    ActorSystemMonitorProbe.apply

  def bindProbe(implicit context: Context): TestProbe[ActorRef[ActorEvent]] = context.bindProbe

  def backoffRefs(implicit context: Context, system: ActorSystem[_]): Seq[classic.ActorRef] =
    context.traverser.getActorTreeFromRootGuardian(system.toClassic)

  "ActorTreeServiceTest" should "bind to ActorEvent" in testCaseSetupContext { sut => implicit context =>
    bindProbe.receiveMessage() should sameOrParent(sut)
    bindProbe.expectNoMessage()
  }

  it should "use backoff traverser at start" in testCaseSetupContext { sut => implicit context =>
    val expectedRefs   = context.traverser.getActorTreeFromRootGuardian(system.toClassic)
    val frontTestProbe = createTestProbe[Seq[classic.ActorRef]]()

    eventually {
      sut ! GetActors(Tag.all, frontTestProbe.ref)
      frontTestProbe.receiveMessage() should contain theSameElementsAs expectedRefs
    }

  }

  it should "store local actor tree snapshot" in testCaseSetupContext { sut => implicit context =>
    val frontTestProbe = createTestProbe[Seq[classic.ActorRef]]()
    val CreatedCount   = 10
    val createdRefs = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set.empty))
    val (terminatedRefs, remainingDetails) = createdRefs.splitAt(CreatedCount / 2)
    val expectedResult                     = remainingDetails.map(_.ref) ++ backoffRefs

    val ref = bindProbe.receiveMessage()
    for {
      create <- createdRefs
    } ref ! ActorCreated(create)

    for {
      terminate <- terminatedRefs
    } terminate.ref.unsafeUpcast[Any] ! PoisonPill

    eventually {
      sut ! GetActors(Tag.all, frontTestProbe.ref)

      frontTestProbe.receiveMessage() should contain theSameElementsAs expectedResult
    }
  }

  it should "respond with actors with specified tags" in testCaseSetupContext { sut => implicit context =>
    val frontTestProbe = createTestProbe[Seq[classic.ActorRef]]()
    val CreatedCount   = 2
    val emptyTags = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set.empty))
    val expectedTags = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set(Tag.stream)))

    val ref = bindProbe.receiveMessage()
    for {
      create <- emptyTags ++ expectedTags
    } ref ! ActorCreated(create)

    eventually {
      sut ! GetActors(Tag.stream, frontTestProbe.ref)
      frontTestProbe.receiveMessage() should contain theSameElementsAs (expectedTags.map(_.ref))
    }
  }

  it should "respond with all actors" in testCaseSetupContext { sut => implicit context =>
    val frontTestProbe = createTestProbe[Seq[classic.ActorRef]]()
    val CreatedCount   = 2
    val emptyTags = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set.empty))
    val streamTags = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set(Tag.stream)))
    val expectedRefs = backoffRefs ++ streamTags.map(_.ref) ++ emptyTags.map(_.ref)

    val ref = bindProbe.receiveMessage()
    for {
      create <- emptyTags ++ streamTags
    } ref ! ActorCreated(create)

    eventually {
      sut ! GetActors(Tag.all, frontTestProbe.ref)

      frontTestProbe.receiveMessage() should contain theSameElementsAs expectedRefs
    }
  }

  it should "assign new tags to actors" in testCaseSetupContext { sut => implicit context =>
    val frontTestProbe = createTestProbe[Seq[classic.ActorRef]]()
    val CreatedCount   = 5
    val RetaggedCount  = 2
    val emptyTags = List
      .fill(CreatedCount)(system.systemActorOf(Behaviors.empty, createUniqueId).toClassic)
      .map(ActorRefDetails(_, Set.empty))
    val retagged = emptyTags.take(RetaggedCount).map(details => TagsSet(details.copy(tags = Set(Tag.stream))))

    val ref = bindProbe.receiveMessage()
    for {
      event <- emptyTags.map(ActorCreated) ++ retagged
    } ref ! event

    eventually {
      sut ! GetActors(Tag.stream, frontTestProbe.ref)

      frontTestProbe.receiveMessage() should contain theSameElementsAs (retagged.map(_.details.ref))
    }
  }

  final case class ActorTreeServiceTestContext(
    monitor: Monitor,
    bindProbe: TestProbe[ActorRef[ActorEvent]],
    traverser: ActorTreeTraverser
  )(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[Monitor]

}
