package io.scalac.core.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem
import io.scalac.extension.metric.ClusterMetricsMonitor.Labels
import io.scalac.extension.metric._
import io.scalac.core.util.TestProbeSynchronized
import io.scalac.core.util.probe.BoundTestProbe._

class ClusterMetricsTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricObserverCommand[Labels]],
  val entityPerRegionProbe: TestProbe[MetricObserverCommand[Labels]],
  val shardRegionsOnNodeProbe: TestProbe[MetricObserverCommand[Labels]],
  val entitiesOnNodeProbe: TestProbe[MetricObserverCommand[Labels]],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand],
  collector: ObserverCollector
)(implicit system: ActorSystem[_])
    extends ClusterMetricsMonitor {

  override def bind(node: Labels): ClusterMetricsMonitor.BoundMonitor =
    new ClusterMetricsMonitor.BoundMonitor with TestProbeSynchronized {

      private type CustomMetricObserver = MetricObserver[Long, Labels] with AsyncTestProbe[_]

      override val shardPerRegions: CustomMetricObserver = ObserverTestProbeWrapper(shardPerRegionsProbe, collector)

      override val entityPerRegion: CustomMetricObserver = ObserverTestProbeWrapper(entityPerRegionProbe, collector)

      override val shardRegionsOnNode: CustomMetricObserver =
        ObserverTestProbeWrapper(shardRegionsOnNodeProbe, collector)

      override val entitiesOnNode: CustomMetricObserver = ObserverTestProbeWrapper(entitiesOnNodeProbe, collector)

      override val reachableNodes: UpDownCounter[Long] with SyncTestProbeWrapper = UpDownCounterTestProbeWrapper(
        reachableNodesProbe
      )

      override val unreachableNodes: UpDownCounter[Long] with SyncTestProbeWrapper = UpDownCounterTestProbeWrapper(
        unreachableNodesProbe
      )

      override val nodeDown: Counter[Long] with SyncTestProbeWrapper =
        UpDownCounterTestProbeWrapper(nodeDownProbe)

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
    val shardPerRegionsProbe    = TestProbe[MetricObserverCommand[Labels]]("shardPerRegionsProbe")
    val entityPerRegionProbe    = TestProbe[MetricObserverCommand[Labels]]("entityPerRegionProbe")
    val shardRegionsOnNodeProbe = TestProbe[MetricObserverCommand[Labels]]("shardRegionsOnNodeProbe")
    val entitiesOnNodeProbe     = TestProbe[MetricObserverCommand[Labels]]("entitiesOnNodeProbe")
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
