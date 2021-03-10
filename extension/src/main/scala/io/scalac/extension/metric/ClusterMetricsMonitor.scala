package io.scalac.extension.metric

import io.scalac.core.model._

object ClusterMetricsMonitor {

  final case class Labels(node: Node, region: Option[Region] = None) {
    def withRegion(region: Region): Labels = copy(region = Some(region))
  }

  implicit val cluterMetrisLablesSerializer: LabelSerializer[Labels] = labels => {
    import labels._
    node.serialize ++ region.serialize
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
