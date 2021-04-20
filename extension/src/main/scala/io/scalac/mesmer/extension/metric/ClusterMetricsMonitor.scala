package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model._

object ClusterMetricsMonitor {

  final case class Labels(node: Node, region: Option[Region] = None) extends LabelSerializable {

    val serialize: RawLabels = node.serialize ++ region.serialize

    def withRegion(region: Region): Labels = copy(region = Some(region))
  }

  trait BoundMonitor extends Synchronized with Bound {
    def shardPerRegions: MetricObserver[Long, Labels]
    def entityPerRegion: MetricObserver[Long, Labels]
    def shardRegionsOnNode: MetricObserver[Long, Labels]
    def entitiesOnNode: MetricObserver[Long, Labels]
    def reachableNodes: UpDownCounter[Long] with Instrument[Long]
    def unreachableNodes: UpDownCounter[Long] with Instrument[Long]
    def nodeDown: Counter[Long] with Instrument[Long]
  }

}
