package io.scalac.extension.metric

import io.scalac.extension.model.Node

object ClusterMetricsMonitor {
  trait BoundMonitor {
    def shardPerRegions: MetricRecorder[Long]
    def entityPerRegion: MetricRecorder[Long]
    def shardRegionsOnNode: MetricRecorder[Long]
    def reachableNodes: Counter[Long]
    def unreachableNodes: Counter[Long]
  }
}

trait ClusterMetricsMonitor {
  import ClusterMetricsMonitor._

  def bind(node: Node): BoundMonitor
}
