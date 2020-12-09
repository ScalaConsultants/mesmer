package io.scalac.extension.metric

import io.scalac.extension.model.Node

object ClusterMetricsMonitor {}

trait ClusterMetricsMonitor extends Bindable[Node] {
  override type Bound <: BoundMonitor

  trait BoundMonitor extends Synchronized {
    def shardPerRegions: MetricRecorder[Long] with Instrument[Long]
    def entityPerRegion: MetricRecorder[Long] with Instrument[Long]
    def shardRegionsOnNode: MetricRecorder[Long] with Instrument[Long]
    def reachableNodes: Counter[Long] with Instrument[Long]
    def unreachableNodes: Counter[Long] with Instrument[Long]
    def nodeDown: UpCounter[Long] with Instrument[Long]
  }
}
