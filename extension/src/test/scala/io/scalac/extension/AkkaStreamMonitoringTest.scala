package io.scalac.extension

import akka.actor.PoisonPill
import akka.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, Behavior }
import akka.{ actor => classic }

import io.scalac.core.akka.model.PushMetrics
import io.scalac.core.model.Tag.{ StageName, SubStreamName }
import io.scalac.core.model._
import io.scalac.extension.AkkaStreamMonitoring.StartStreamCollection
import io.scalac.extension.event.ActorInterpreterStats
import io.scalac.extension.util.TestConfig.localActorProvider
import io.scalac.extension.util.probe.BoundTestProbe.{ LazyMetricsObserved, MetricObserved }
import io.scalac.extension.util.probe.{ StreamMonitorTestProbe, StreamOperatorMonitorTestProbe }
import io.scalac.extension.util.{ MonitorFixture, TerminationRegistryOps, TestOps }
import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.enablers.Emptiness.emptinessOfOption
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

import io.scalac.extension.util.probe.ObserverCollector.CommonCollectorImpl

class AkkaStreamMonitoringTest
    extends ScalaTestWithActorTestKit(localActorProvider)
    with AnyFlatSpecLike
    with Matchers
    with Inspectors
    with Eventually
    with OptionValues
    with Inside
    with BeforeAndAfterAll
    with TerminationRegistryOps
    with LoneElement
    with TestOps
    with MonitorFixture {

  override type Monitor = (StreamOperatorMonitorTestProbe, StreamMonitorTestProbe)
  override type Command = AkkaStreamMonitoring.Command
  val OperationsPing = 1.seconds
  val ReceiveWait    = OperationsPing * 3
  val SourceName     = "sourceSingle"
  val SinkName       = "sinkIgnore"
  val FlowName       = "map"
  val StagesNames    = Seq(SourceName, SinkName, FlowName)

  override protected val serviceKey: Option[ServiceKey[_]] = None

  def singleActorLinearShellInfo(subStreamName: SubStreamName, flowCount: Int, push: Long, pull: Long): ShellInfo = {
    val source = StageInfo(StageName(SourceName, 0), subStreamName)
    val sink   = StageInfo(StageName(SinkName, flowCount + 1), subStreamName)
    val flows  = List.tabulate(flowCount)(id => StageInfo(StageName(FlowName, id + 1), subStreamName))

    val connections = (flows.tail :+ sink)
      .scanLeft(ConnectionStats(flows.head.stageName, source.stageName, pull, push)) {
        case (ConnectionStats(in, _, _, _), next) => ConnectionStats(next.stageName, in, pull, push)
      }

    (source +: flows :+ sink).toArray -> connections.toArray

  }

  def akkaStreamActorBehavior(
    shellInfo: Set[ShellInfo],
    subStream: SubStreamName,
    monitor: Option[ActorRef[PushMetrics]]
  ): Behavior[PushMetrics] = Behaviors.receive {
    case (ctx, pm @ PushMetrics(ref)) =>
      monitor.foreach(_ ! pm)
      ref ! ActorInterpreterStats(ctx.self.toClassic, subStream, shellInfo)
      Behaviors.same
  }

  def cleanActors(refs: Seq[classic.ActorRef]): Unit = refs.foreach(_ ! PoisonPill)

  override protected def createMonitor: Monitor = {
    val operations = StreamOperatorMonitorTestProbe(new CommonCollectorImpl(OperationsPing))
    val global     = StreamMonitorTestProbe(new CommonCollectorImpl(OperationsPing))
    (operations, global)
  }

  override protected def setUp(monitor: Monitor, cache: Boolean): ActorRef[Command] = monitor match {
    case (operations, global) => system.systemActorOf(AkkaStreamMonitoring(operations, global, None), createUniqueId)
  }

  "AkkaStreamMonitoring" should "ask all received refs for metrics" in testWithRef {
    case ((_, _), sut) =>
      val probes = List.fill(5)(TestProbe[PushMetrics]())
      val refs = probes
        .map(probe => akkaStreamActorBehavior(Set.empty, SubStreamName(randomString(10), "1"), Some(probe.ref)))
        .map(behavior => system.systemActorOf(behavior, createUniqueId).toClassic)

      sut ! StartStreamCollection(refs.toSet)

      forAll(probes)(_.expectMessageType[PushMetrics])

      cleanActors(refs)
  }

  it should "publish amount of actors running stream" in testWithRef {
    case ((_, global), sut) =>
      val ExpectedCount = 5
      val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.map {
        case (name, index) =>
          val streamName = SubStreamName(s"$name-$index", s"$index")
          val behavior   = akkaStreamActorBehavior(Set.empty, streamName, None)
          system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
      }

      sut ! StartStreamCollection(refs.toSet)

      global.streamActorsProbe.receiveMessage(ReceiveWait) shouldBe (MetricObserved(ExpectedCount))

      cleanActors(refs)
  }

  it should "publish amount of running streams" in testWithRef {
    case ((_, global), sut) =>
      val ExpectedCount  = 5
      val ActorPerStream = 3
      val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.flatMap {
        case (name, index) =>
          List.tabulate(ActorPerStream) { streamId =>
            val streamName = SubStreamName(s"$name-$index", s"$streamId")
            val behavior   = akkaStreamActorBehavior(Set.empty, streamName, None)
            system.systemActorOf(behavior, s"$name-$index-$streamId-${randomString(10)}").toClassic
          }
      }

      sut ! StartStreamCollection(refs.toSet)

      global.runningStreamsProbe.receiveMessage(2.seconds) shouldBe (MetricObserved(ExpectedCount))
      global.streamActorsProbe.receiveMessage(2.seconds) shouldBe (MetricObserved(ExpectedCount * ActorPerStream))

      cleanActors(refs)
  }

  it should "collect amount of messages processed and operators" in testWithRef {
    case ((operations, _), sut) =>
      val ExpectedCount = 5
      val Flows         = 2
      val Push          = 11L
      val Pull          = 9L

      val refs = generateUniqueString(ExpectedCount, 10).zipWithIndex.map {
        case (name, index) =>
          val streamName = SubStreamName(s"$name-$index", "0")

          val linearShellInfo = singleActorLinearShellInfo(streamName, Flows, Push, Pull)

          val behavior = akkaStreamActorBehavior(Set(linearShellInfo), streamName, None)

          system.systemActorOf(behavior, s"$name-$index-$index-${randomString(10)}").toClassic
      }

      sut ! StartStreamCollection(refs.toSet)

      val operators =
        operations.runningOperatorsTestProbe.receiveMessages(ExpectedCount * StagesNames.size, OperationsPing)
      val processed = operations.processedTestProbe.receiveMessages(ExpectedCount * (Flows + 1), OperationsPing)

      forAll(processed)(inside(_) {
        case LazyMetricsObserved(value, labels) =>
          value shouldBe Push
          labels.node shouldBe empty
      })

      operators.map(_.labels.operator.name).distinct should contain theSameElementsAs StagesNames

      cleanActors(refs)
  }

}
