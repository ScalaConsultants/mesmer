package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.extension.metric.ClusterMetricsMonitor.Labels
import io.scalac.extension.metric._
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe._

class ClusterMetricsTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricObserverCommand],
  val entityPerRegionProbe: TestProbe[MetricObserverCommand],
  val shardRegionsOnNodeProbe: TestProbe[MetricObserverCommand],
  val entitiesOnNodeProbe: TestProbe[MetricObserverCommand],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand]
)(implicit system: ActorSystem[_])
    extends ClusterMetricsMonitor {

  override def bind(node: Labels): ClusterMetricsMonitor.BoundMonitor =
    new ClusterMetricsMonitor.BoundMonitor with TestProbeSynchronized {

      override val shardPerRegions: MetricObserver[Long] with AbstractTestProbeWrapper = ObserverTestProbeWrapper(
        shardPerRegionsProbe
      )

      override val entityPerRegion: MetricObserver[Long] with AbstractTestProbeWrapper = ObserverTestProbeWrapper(
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

      override val nodeDown: UpCounter[Long] with AbstractTestProbeWrapper =
        CounterTestProbeWrapper(nodeDownProbe)

      override def unbind(): Unit = ()
    }
}

object ClusterMetricsTestProbe {
  def apply()(implicit system: ActorSystem[_]): ClusterMetricsTestProbe = {
    val shardPerRegionsProbe    = TestProbe[MetricObserverCommand]("shardPerRegionsProbe")
    val entityPerRegionProbe    = TestProbe[MetricObserverCommand]("entityPerRegionProbe")
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
