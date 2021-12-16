package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaClusterModule

object ClusterMetricsMonitor {

  final case class Attributes(node: Node, region: Option[Region] = None) extends AttributesSerializable {

    val serialize: RawAttributes = node.serialize ++ region.serialize

    def withRegion(region: Region): Attributes = copy(region = Some(region))
  }

  trait BoundMonitor extends Synchronized with Bound with AkkaClusterModule.All[Metric[Long]] {
    def shardPerRegions: MetricObserver[Long, Attributes]
    def entityPerRegion: MetricObserver[Long, Attributes]
    def shardRegionsOnNode: MetricObserver[Long, Attributes]
    def entitiesOnNode: MetricObserver[Long, Attributes]
    def reachableNodes: UpDownCounter[Long] with Instrument[Long]
    def unreachableNodes: UpDownCounter[Long] with Instrument[Long]
    def nodeDown: Counter[Long] with Instrument[Long]
  }

}
