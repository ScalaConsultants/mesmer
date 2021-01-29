package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.{ ClusterMetricsMonitor, Counter, MetricObserver, MetricRecorder, UpCounter }
import io.scalac.extension.model.Node
import io.scalac.extension.util.probe.BoundTestProbe._
import io.scalac.extension.util.{ probe, TestProbeSynchronized }

class ClusterMetricsTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricRecorderCommand],
  val entityPerRegionProbe: TestProbe[MetricRecorderCommand],
  val shardRegionsOnNodeProbe: TestProbe[MetricObserverCommand],
  val entitiesOnNodeProbe: TestProbe[MetricObserverCommand],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand]
)(implicit system: ActorSystem[_])
    extends ClusterMetricsMonitor {

  override type Bound = BoundMonitor

  override def bind(node: Node): Bound = new BoundMonitor with TestProbeSynchronized {

    override val shardPerRegions: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      shardPerRegionsProbe
    )

    override val entityPerRegion: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      entityPerRegionProbe
    )

    override val shardRegionsOnNode: MetricObserver[Long] with AbstractTestProbeWrapper = ObserverTestProbeWrapper(
      shardRegionsOnNodeProbe
    )

    override val entitiesOnNode: MetricObserver[Long] with AbstractTestProbeWrapper = ObserverTestProbeWrapper(
      entitiesOnNodeProbe
    )

    override val reachableNodes: Counter[Long] with AbstractTestProbeWrapper = CounterTestProbeWrapper(
      reachableNodesProbe
    )

    override val unreachableNodes: Counter[Long] with AbstractTestProbeWrapper = CounterTestProbeWrapper(
      unreachableNodesProbe
    )

    override val nodeDown: UpCounter[Long] with AbstractTestProbeWrapper = CounterTestProbeWrapper(nodeDownProbe)
  }
}

object ClusterMetricsTestProbe {
  def apply()(implicit system: ActorSystem[_]): ClusterMetricsTestProbe = {
    val shardPerRegionsProbe    = TestProbe[MetricRecorderCommand]("shardPerRegionsProbe")
    val entityPerRegionProbe    = TestProbe[MetricRecorderCommand]("entityPerRegionProbe")
    val shardRegionsOnNodeProbe = TestProbe[MetricObserverCommand]("shardRegionsOnNodeProbe")
    val entitiesOnNodeProbe     = TestProbe[MetricObserverCommand]("entitiesOnNodeProbe")
    val reachableNodesProbe     = TestProbe[CounterCommand]("reachableNodesProbe")
    val unreachableNodesProbe   = TestProbe[CounterCommand]("unreachableNodesProbe")
    val nodeDownProbe           = TestProbe[CounterCommand]("nodeDownProbe")
    new ClusterMetricsTestProbe(
      shardPerRegionsProbe,
      entityPerRegionProbe,
      shardRegionsOnNodeProbe,
      entitiesOnNodeProbe,
      reachableNodesProbe,
      unreachableNodesProbe,
      nodeDownProbe
    )
  }
}
