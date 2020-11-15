package io.scalac.extension.metric

import io.scalac.extension.model.Node

trait ClusterMetricsMonitor extends Bindable[Node] {

  override type Bound = BoundMonitor

  override def bind(node: Node): BoundMonitor

  trait BoundMonitor {
    def shardPerRegions: MetricRecorder[Long]
    def entityPerRegion: MetricRecorder[Long]
    def shardRegionsOnNode: MetricRecorder[Long]
    def reachableNodes: Counter[Long]
    def unreachableNodes: Counter[Long]
  }
}
