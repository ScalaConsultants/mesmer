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
  val nodeDownProbe: TestProbe[CounterCommand],
  collector: ObserverCollector
)(implicit system: ActorSystem[_])
    extends ClusterMetricsMonitor {

  override def bind(node: Labels): ClusterMetricsMonitor.BoundMonitor =
    new ClusterMetricsMonitor.BoundMonitor with TestProbeSynchronized {

      private type CustomMetricObserver = MetricObserver[Long] with AsyncTestProbe[_]

      override val shardPerRegions: CustomMetricObserver = ObserverTestProbeWrapper(shardPerRegionsProbe, collector)

      override val entityPerRegion: CustomMetricObserver = ObserverTestProbeWrapper(entityPerRegionProbe, collector)

      override val shardRegionsOnNode: CustomMetricObserver =
        ObserverTestProbeWrapper(shardRegionsOnNodeProbe, collector)

      override val entitiesOnNode: CustomMetricObserver = ObserverTestProbeWrapper(entitiesOnNodeProbe, collector)

      override val reachableNodes: Counter[Long] with SyncTestProbeWrapper = CounterTestProbeWrapper(
        reachableNodesProbe
      )

      override val unreachableNodes: Counter[Long] with SyncTestProbeWrapper = CounterTestProbeWrapper(
        unreachableNodesProbe
      )

      override val nodeDown: UpCounter[Long] with SyncTestProbeWrapper =
        CounterTestProbeWrapper(nodeDownProbe)

      override def unbind(): Unit = {
        collector.finish(shardPerRegionsProbe)
        collector.finish(entityPerRegionProbe)
        collector.finish(shardRegionsOnNodeProbe)
        collector.finish(entitiesOnNodeProbe)
      }
    }
}

object ClusterMetricsTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): ClusterMetricsTestProbe = {
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
      nodeDownProbe,
      collector
    )
  }
}
