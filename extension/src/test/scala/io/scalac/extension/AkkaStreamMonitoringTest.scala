package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.event.EventBus
import io.scalac.core.event.Service.streamService
import io.scalac.core.event.StreamEvent.StreamInterpreterStats
import io.scalac.core.model.Tag
import io.scalac.core.model.Tag.StageName
import io.scalac.core.model.Tag.SubStreamName
import io.scalac.core.model._
import io.scalac.core.util.TestCase.MonitorTestCaseContext
import io.scalac.core.util.TestCase.MonitorWithServiceTestCaseFactory
import io.scalac.core.util.TestCase.ProvidedActorSystemTestCaseFactory
import io.scalac.core.util.TestConfig
import io.scalac.core.util.TestOps
import io.scalac.core.util.probe.ObserverCollector
import io.scalac.core.util.probe.ObserverCollector.ScheduledCollectorImpl
import io.scalac.core.util.probe.{ Collected => CollectedObserver }
import io.scalac.extension.service.ActorTreeService
import io.scalac.extension.service.ActorTreeService.GetActors
import io.scalac.extension.util.probe
import io.scalac.extension.util.probe.BoundTestProbe.MetricObserved
import io.scalac.extension.util.probe.BoundTestProbe.MetricRecorded
import io.scalac.extension.util.probe.StreamMonitorTestProbe
import io.scalac.extension.util.probe.StreamOperatorMonitorTestProbe

class AkkaStreamMonitoringTest
    extends ScalaTestWithActorTestKit(TestConfig.localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with Eventually
    with OptionValues
    with Inside
    with BeforeAndAfterAll
    with LoneElement
    with TestOps
    with ProvidedActorSystemTestCaseFactory
    with MonitorWithServiceTestCaseFactory {

  type Monitor = (StreamOperatorMonitorTestProbe, StreamMonitorTestProbe)
  type Context = TestCaseContext
  type Command = AkkaStreamMonitoring.Command

  protected val serviceKey: ServiceKey[_] = streamService.serviceKey

  protected def createContextFromMonitor(monitor: (StreamOperatorMonitorTestProbe, StreamMonitorTestProbe))(implicit
    system: ActorSystem[_]
  ): TestCaseContext =
    TestCaseContext(monitor, TestProbe(), monitor._1.collector)

  protected def createMonitorBehavior(implicit context: Context): Behavior[Command] =
    Behaviors.setup[Command](ctx =>
      Behaviors.withTimers(scheduler =>
        new AkkaStreamMonitoring(ctx, operations, global, scheduler, None, treeService.ref).start()
      )
    )

  private final val OperationsPing = 3.seconds
  private final val ReceiveWait    = OperationsPing * 3
  private final val SourceName     = "sourceSingle"
  private final val SinkName       = "sinkIgnore"
  private final val FlowName       = "map"
  private final val StagesNames    = Seq(SourceName, SinkName, FlowName)

  override protected def createMonitor(implicit system: ActorSystem[_]): Monitor = {
    val collector = new ScheduledCollectorImpl(OperationsPing)
    (
      probe.StreamOperatorMonitorTestProbe(collector),
      probe.StreamMonitorTestProbe(collector)
    )
  }

  def singleActorLinearShellInfo(subStreamName: SubStreamName, flowCount: Int, push: Long, pull: Long): ShellInfo = {
    val source = StageInfo(0, StageName(SourceName, 0), subStreamName)
    val sink   = StageInfo(flowCount + 1, StageName(SinkName, flowCount + 1), subStreamName)
    val flows = List.tabulate(flowCount) { id =>
      val stageId = id + 1
      StageInfo(stageId, StageName(FlowName, stageId), subStreamName)
    }

    val connections = (flows.tail :+ sink)
      .scanLeft(ConnectionStats(flows.head.id, source.id, pull, push)) { case (ConnectionStats(in, _, _, _), next) =>
        ConnectionStats(next.id, in, pull, push)
      }

    (source +: flows :+ sink).toArray -> connections.toArray
  }

  def akkaStreamActorBehavior(
    shellInfo: Set[ShellInfo],
    subStream: SubStreamName,
    monitor: Option[ActorRef[PushMetrics.type]]
  ): Behavior[PushMetrics.type] = Behaviors.receive { case (ctx, PushMetrics) =>
    monitor.foreach(_ ! PushMetrics)
    EventBus(ctx.system).publishEvent(StreamInterpreterStats(ctx.self.toClassic, subStream, shellInfo))
    Behaviors.same
  }

  def sut(implicit s: Setup): ActorRef[AkkaStreamMonitoring.Command] =
    s.asInstanceOf[ActorRef[AkkaStreamMonitoring.Command]]

  def global(implicit c: Context): StreamMonitorTestProbe             = c.monitor._2
  def operations(implicit c: Context): StreamOperatorMonitorTestProbe = c.monitor._1

  def treeService(implicit c: Context): TestProbe[ActorTreeService.Command] = c.actorTreeService

  "AkkaStreamMonitoring" should "ask all received refs for metrics" in testCaseSetupContext {
    implicit setup => implicit c =>
      val probes = List.fill(5)(TestProbe[PushMetrics.type]())
      val refs = probes
        .map(probe => akkaStreamActorBehavior(Set.empty, SubStreamName(randomString(10), "1"), Some(probe.ref)))
        .map(behavior => system.systemActorOf(behavior, createUniqueId).toClassic)

      inside(treeService.receiveMessage()) { case GetActors(Tag.stream, reply) =>
        reply ! refs
      }

      forAll(probes)(_.expectMessageType[PushMetrics.type])
  }

  it should "publish amount of actors running stream" in testCaseSetupContext { implicit setup => implicit c =>
    val ExpectedCount = 5
    val refs = generateUniqueStringSeq(ExpectedCount, 10).zipWithIndex.map { case (name, index) =>
      val streamName = SubStreamName(s"$name-$index", s"$index")
      val behavior   = akkaStreamActorBehavior(Set.empty, streamName, None)
      system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
    }

//    sut ! StartStreamCollection
    inside(treeService.receiveMessage()) { case GetActors(Tag.stream, reply) =>
      reply ! refs
    }

    global.streamActorsProbe.receiveMessage(ReceiveWait) shouldBe (MetricRecorded(ExpectedCount))
  }

  it should "publish amount of running streams" in testCaseSetupContext { implicit setup => implicit c =>
    val ExpectedCount  = 5
    val ActorPerStream = 3
    val refs = generateUniqueStringSeq(ExpectedCount, 10).zipWithIndex.flatMap { case (name, index) =>
      List.tabulate(ActorPerStream) { streamId =>
        val streamName = SubStreamName(s"$name-$index", s"$streamId")
        val behavior   = akkaStreamActorBehavior(Set.empty, streamName, None)
        system.systemActorOf(behavior, s"$name-$index-$streamId-${randomString(10)}").toClassic
      }
    }

//    sut ! StartStreamCollection
    inside(treeService.receiveMessage()) { case GetActors(Tag.stream, reply) =>
      reply ! refs
    }

    global.runningStreamsProbe.receiveMessage(2.seconds) shouldBe (MetricRecorded(ExpectedCount))
    global.streamActorsProbe.receiveMessage(2.seconds) shouldBe (MetricRecorded(ExpectedCount * ActorPerStream))
  }

  it should "collect amount of messages processed, demand and operators" in testCaseSetupContext {
    implicit setup => implicit c =>
      val ExpectedCount = 5
      val Flows         = 2
      val Push          = 11L
      val Pull          = 9L

      val refs = generateUniqueStringSeq(ExpectedCount, 10).zipWithIndex.map { case (name, index) =>
        val streamName = SubStreamName(s"$name-$index", "0")

        val linearShellInfo = singleActorLinearShellInfo(streamName, Flows, Push, Pull)

        val behavior = akkaStreamActorBehavior(Set(linearShellInfo), streamName, None)

        system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
      }

      inside(treeService.receiveMessage()) { case GetActors(Tag.stream, reply) =>
        reply ! refs
      }

      val operators =
        operations.runningOperatorsTestProbe.receiveMessages(ExpectedCount * StagesNames.size, OperationsPing)

      val processed = operations.processedTestProbe.receiveMessages(ExpectedCount * (Flows + 1), OperationsPing)
      val demand    = operations.demandTestProbe.receiveMessages(ExpectedCount * (Flows + 1), OperationsPing)

      forAll(processed)(inside(_) { case MetricObserved(value, labels) =>
        value shouldBe Push
        labels.node shouldBe empty
      })

      operators.collect { case MetricObserved(_, labels) =>
        labels.operator.name
      }.distinct should contain theSameElementsAs StagesNames

      forAll(demand)(inside(_) { case MetricObserved(value, labels) =>
        value shouldBe Pull
        labels.node shouldBe empty
      })
  }

  case class TestCaseContext(
    monitor: Monitor,
    actorTreeService: TestProbe[ActorTreeService.Command],
    collector: ObserverCollector
  )(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[Monitor]
      with CollectedObserver

}
