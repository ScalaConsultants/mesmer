package io.scalac.extension.metric

import io.scalac.extension.model.Node

object ClusterMetricsMonitor {

  case class Labels(node: Node, region: Option[String]) {
    def withRegion(region: String): Labels = copy(region = Some(region))
  }

  trait BoundMonitor extends Synchronized with Bound {
    def shardPerRegions: MetricObserver[Long]
    def entityPerRegion: MetricObserver[Long]
    def shardRegionsOnNode: MetricObserver[Long]
    def entitiesOnNode: MetricObserver[Long]
    def reachableNodes: Counter[Long] with Instrument[Long]
    def unreachableNodes: Counter[Long] with Instrument[Long]
    def nodeDown: UpCounter[Long] with Instrument[Long]
  }

}
