package io.scalac.mesmer.extension.util.probe

import akka.actor.testkit.typed.scaladsl.TestProbe
import akka.actor.typed.ActorSystem

import io.scalac.mesmer.core.util.probe.ObserverCollector
import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor.Attributes
import io.scalac.mesmer.extension.metric._
import io.scalac.mesmer.extension.util.TestProbeSynchronized
import io.scalac.mesmer.extension.util.probe.BoundTestProbe._

class ClusterMonitorTestProbe private (
  val shardPerRegionsProbe: TestProbe[MetricObserverCommand[Attributes]],
  val entityPerRegionProbe: TestProbe[MetricObserverCommand[Attributes]],
  val shardRegionsOnNodeProbe: TestProbe[MetricObserverCommand[Attributes]],
  val entitiesOnNodeProbe: TestProbe[MetricObserverCommand[Attributes]],
  val reachableNodesProbe: TestProbe[CounterCommand],
  val unreachableNodesProbe: TestProbe[CounterCommand],
  val nodeDownProbe: TestProbe[CounterCommand],
  collector: ObserverCollector
)(implicit system: ActorSystem[_])
    extends ClusterMetricsMonitor {

  def bind(node: Attributes): ClusterMetricsMonitor.BoundMonitor =
    new ClusterMetricsMonitor.BoundMonitor with TestProbeSynchronized {

      private type CustomMetricObserver = MetricObserver[Long, Attributes] with AsyncTestProbe[_]

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
    val shardPerRegionsProbe    = TestProbe[MetricObserverCommand[Attributes]]("shardPerRegionsProbe")
    val entityPerRegionProbe    = TestProbe[MetricObserverCommand[Attributes]]("entityPerRegionProbe")
    val shardRegionsOnNodeProbe = TestProbe[MetricObserverCommand[Attributes]]("shardRegionsOnNodeProbe")
    val entitiesOnNodeProbe     = TestProbe[MetricObserverCommand[Attributes]]("entitiesOnNodeProbe")
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
