package io.scalac.extension

import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, ActorSystem, Behavior }
import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model.Tag.{ StageName, SubStreamName }
import io.scalac.core.model._
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.event.ActorInterpreterStats
import io.scalac.extension.util.TestCase.{
  AbstractMonitorTestCaseFactory,
  MonitorTestCaseContext,
  NoSetupTestCaseFactory,
  ProvidedActorSystemTestCaseFactory
}
import io.scalac.extension.util.probe.BoundTestProbe.{ LazyMetricsObserved, MetricObserved }
import io.scalac.extension.util.probe.ObserverCollector.CommonCollectorImpl
import io.scalac.extension.util.probe.{ StreamMonitorTestProbe, StreamOperatorMonitorTestProbe }
import io.scalac.extension.util.{ TestConfig, TestOps }
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.enablers.Emptiness.emptinessOfOption
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

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
    with AbstractMonitorTestCaseFactory
    with ProvidedActorSystemTestCaseFactory
    with NoSetupTestCaseFactory {

  type Monitor = (StreamOperatorMonitorTestProbe, StreamMonitorTestProbe)
  type Context = TestCaseContext

  private val OperationsPing = 1.seconds
  private val ReceiveWait    = OperationsPing * 3
  private val SourceName     = "sourceSingle"
  private val SinkName       = "sinkIgnore"
  private val FlowName       = "map"
  private val StagesNames    = Seq(SourceName, SinkName, FlowName)

  override protected def createMonitor(implicit system: ActorSystem[_]): Monitor =
    (
      StreamOperatorMonitorTestProbe(new CommonCollectorImpl(OperationsPing)),
      StreamMonitorTestProbe(new CommonCollectorImpl(OperationsPing))
    )

  override protected def createContextFromMonitor(
    monitor: Monitor
  )(implicit system: ActorSystem[_]): Context = {
    val (operations, global) = monitor
    val ref                  = system.systemActorOf(AkkaStreamMonitoring(operations, global, None), createUniqueId)
    TestCaseContext(monitor, ref)
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
    monitor: Option[ActorRef[PushMetrics]]
  ): Behavior[PushMetrics] = Behaviors.receive { case (ctx, pm @ PushMetrics(ref)) =>
    monitor.foreach(_ ! pm)
    ref ! ActorInterpreterStats(ctx.self.toClassic, subStream, shellInfo)
    Behaviors.same
  }

  def sut(implicit c: TestCaseContext): ActorRef[AkkaStreamMonitoring.Command] = c.ref
  def global(implicit c: TestCaseContext): StreamMonitorTestProbe              = c.monitor._2
  def operations(implicit c: TestCaseContext): StreamOperatorMonitorTestProbe  = c.monitor._1

  "AkkaStreamMonitoring" should "ask all received refs for metrics" in testCase { implicit c =>
    val probes = List.fill(5)(TestProbe[PushMetrics]())
    val refs = probes
      .map(probe => akkaStreamActorBehavior(Set.empty, SubStreamName(randomString(10), "1"), Some(probe.ref)))
      .map(behavior => system.systemActorOf(behavior, createUniqueId).toClassic)

    sut ! StartStreamCollection(refs.toSet)

    forAll(probes)(_.expectMessageType[PushMetrics])
  }

  it should "publish amount of actors running stream" in testCase { implicit c =>
    val ExpectedCount = 5
    val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.map { case (name, index) =>
      val streamName = SubStreamName(s"$name-$index", s"$index")
      val behavior   = akkaStreamActorBehavior(Set.empty, streamName, None)
      system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
    }

    sut ! StartStreamCollection(refs.toSet)

    global.streamActorsProbe.receiveMessage(ReceiveWait) shouldBe (MetricObserved(ExpectedCount))
  }

  it should "publish amount of running streams" in testCase { implicit c =>
    val ExpectedCount  = 5
    val ActorPerStream = 3
    val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.flatMap { case (name, index) =>
      List.tabulate(ActorPerStream) { streamId =>
        val streamName = SubStreamName(s"$name-$index", s"$streamId")
        val behavior   = akkaStreamActorBehavior(Set.empty, streamName, None)
        system.systemActorOf(behavior, s"$name-$index-$streamId-${randomString(10)}").toClassic
      }
    }

    sut ! StartStreamCollection(refs.toSet)

    global.runningStreamsProbe.receiveMessage(2.seconds) shouldBe (MetricObserved(ExpectedCount))
    global.streamActorsProbe.receiveMessage(2.seconds) shouldBe (MetricObserved(ExpectedCount * ActorPerStream))
  }

  it should "collect amount of messages processed and operators" in testCase { implicit c =>
    val ExpectedCount = 5
    val Flows         = 2
    val Push          = 11L
    val Pull          = 9L

    val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.map { case (name, index) =>
      val streamName = SubStreamName(s"$name-$index", "0")

      val linearShellInfo = singleActorLinearShellInfo(streamName, Flows, Push, Pull)

      val behavior = akkaStreamActorBehavior(Set(linearShellInfo), streamName, None)

      system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
    }

    sut ! StartStreamCollection(refs.toSet)

    val operators =
      operations.runningOperatorsTestProbe.receiveMessages(ExpectedCount * StagesNames.size, OperationsPing)
    val processed = operations.processedTestProbe.receiveMessages(ExpectedCount * (Flows + 1), OperationsPing)

    forAll(processed)(inside(_) { case LazyMetricsObserved(value, labels) =>
      value shouldBe Push
      labels.node shouldBe empty
    })

    operators.map(_.labels.operator.name).distinct should contain theSameElementsAs StagesNames
  }

  case class TestCaseContext(monitor: Monitor, ref: ActorRef[AkkaStreamMonitoring.Command])(implicit
    val system: ActorSystem[_]
  ) extends MonitorTestCaseContext[Monitor]

}
