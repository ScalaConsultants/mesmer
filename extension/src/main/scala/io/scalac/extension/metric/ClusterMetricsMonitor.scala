package io.scalac.extension.metric

import io.scalac.extension.model.Node

object ClusterMetricsMonitor {

  type Labels = Node

  trait BoundMonitor extends Synchronized with Bound {
    def shardPerRegions(region: String): MetricObserver[Long]
    def entityPerRegion(region: String): MetricObserver[Long]
    def shardRegionsOnNode: MetricObserver[Long]
    def entitiesOnNode: MetricObserver[Long]
    def reachableNodes: Counter[Long] with Instrument[Long]
    def unreachableNodes: Counter[Long] with Instrument[Long]
    def nodeDown: UpCounter[Long] with Instrument[Long]
  }

}
