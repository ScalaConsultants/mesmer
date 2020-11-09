package io.scalac.extension.metric

import io.scalac.extension.model.Node

trait ClusterMetricsMonitor {

  def bind(node: Node): BoundClusterMetricsMonitor

  trait BoundClusterMetricsMonitor {
    def shardPerRegions: MetricRecorder[Long]
    def entityPerRegion: MetricRecorder[Long]
    def shardRegionsOnNode: MetricRecorder[Long]
    def reachableNodes: Counter[Long]
    def unreachableNodes: Counter[Long]
  }
}
