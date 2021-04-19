package io.scalac.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.core.util.probe.ObserverCollector
import io.scalac.extension.metric.ClusterMetricsMonitor.Labels
import io.scalac.extension.metric._
import io.scalac.extension.util.TestProbeSynchronized
import io.scalac.extension.util.probe.BoundTestProbe._

class ClusterMonitorTestProbe private (
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

  def bind(node: Labels): ClusterMetricsMonitor.BoundMonitor =
    new ClusterMetricsMonitor.BoundMonitor with TestProbeSynchronized {

      private type CustomMetricObserver = MetricObserver[Long, Labels] with AsyncTestProbe[_]

      val shardPerRegions: CustomMetricObserver = ObserverTestProbeWrapper(shardPerRegionsProbe, collector)

      val entityPerRegion: CustomMetricObserver = ObserverTestProbeWrapper(entityPerRegionProbe, collector)

      val shardRegionsOnNode: CustomMetricObserver =
        ObserverTestProbeWrapper(shardRegionsOnNodeProbe, collector)

      val entitiesOnNode: CustomMetricObserver = ObserverTestProbeWrapper(entitiesOnNodeProbe, collector)

      val reachableNodes: UpDownCounter[Long] with SyncTestProbeWrapper = UpDownCounterTestProbeWrapper(
        reachableNodesProbe
      )

      val unreachableNodes: UpDownCounter[Long] with SyncTestProbeWrapper = UpDownCounterTestProbeWrapper(
        unreachableNodesProbe
      )

      val nodeDown: Counter[Long] with SyncTestProbeWrapper =
        UpDownCounterTestProbeWrapper(nodeDownProbe)

      def unbind(): Unit = {
        collector.finish(shardPerRegionsProbe)
        collector.finish(entityPerRegionProbe)
        collector.finish(shardRegionsOnNodeProbe)
        collector.finish(entitiesOnNodeProbe)
      }
    }
}

object ClusterMonitorTestProbe {
  def apply(collector: ObserverCollector)(implicit system: ActorSystem[_]): ClusterMonitorTestProbe = {
    val shardPerRegionsProbe    = TestProbe[MetricObserverCommand[Labels]]("shardPerRegionsProbe")
    val entityPerRegionProbe    = TestProbe[MetricObserverCommand[Labels]]("entityPerRegionProbe")
    val shardRegionsOnNodeProbe = TestProbe[MetricObserverCommand[Labels]]("shardRegionsOnNodeProbe")
    val entitiesOnNodeProbe     = TestProbe[MetricObserverCommand[Labels]]("entitiesOnNodeProbe")
    val reachableNodesProbe     = TestProbe[CounterCommand]("reachableNodesProbe")
    val unreachableNodesProbe   = TestProbe[CounterCommand]("unreachableNodesProbe")
    val nodeDownProbe           = TestProbe[CounterCommand]("nodeDownProbe")
    new ClusterMonitorTestProbe(
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
