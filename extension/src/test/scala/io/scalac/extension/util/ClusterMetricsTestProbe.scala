package io.scalac.extension.util

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.{ ClusterMetricsMonitor, Counter, MetricRecorder, UpCounter }
import io.scalac.extension.model.Node
import io.scalac.extension.util.BoundTestProbe._

class ClusterMetricsTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricRecorderCommand],
  val entityPerRegionProbe: TestProbe[MetricRecorderCommand],
  val shardRegionsOnNodeProbe: TestProbe[MetricRecorderCommand],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand]
) extends ClusterMetricsMonitor {

  override type Bound = BoundMonitor

  override def bind(node: Node): Bound = new BoundMonitor with TestProbeSynchronized {

    override val shardPerRegions: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      shardPerRegionsProbe
    )

    override val entityPerRegion: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      entityPerRegionProbe
    )

    override val shardRegionsOnNode: MetricRecorder[Long] with AbstractTestProbeWrapper = RecorderTestProbeWrapper(
      shardRegionsOnNodeProbe
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
    val shardRegionsOnNodeProbe = TestProbe[MetricRecorderCommand]("shardRegionsOnNodeProbe")
    val reachableNodesProbe     = TestProbe[CounterCommand]("reachableNodesProbe")
    val unreachableNodesProbe   = TestProbe[CounterCommand]("unreachableNodesProbe")
    val nodeDownProbe           = TestProbe[CounterCommand]("nodeDownProbe")
    new ClusterMetricsTestProbe(
      shardPerRegionsProbe,
      entityPerRegionProbe,
      shardRegionsOnNodeProbe,
      reachableNodesProbe,
      unreachableNodesProbe,
      nodeDownProbe
    )
  }
}
