package io.scalac.extension.metric

import io.scalac.extension.model.Node

object ClusterMetricsMonitor {}

trait ClusterMetricsMonitor extends Bindable[Node] {
  override type Bound <: BoundMonitor

  trait BoundMonitor extends Synchronized {
    def shardPerRegions(region: String): MetricObserver[Long]
    def entityPerRegion(region: String): MetricObserver[Long]
    def shardRegionsOnNode: MetricObserver[Long]
    def entitiesOnNode: MetricObserver[Long]
    def reachableNodes: Counter[Long] with Instrument[Long]
    def unreachableNodes: Counter[Long] with Instrument[Long]
    def nodeDown: UpCounter[Long] with Instrument[Long]
  }
}
