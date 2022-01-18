package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model.Tag.StreamName
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaStreamModule

object StreamMetricsMonitor {

  final case class EagerAttributes(
    node: Option[Node]
  ) extends AttributesSerializable {
    lazy val serialize: RawAttributes = node.serialize
  }

  final case class Attributes(node: Option[Node], streamName: StreamName) extends AttributesSerializable {
    lazy val serialize: RawAttributes = node.serialize ++ streamName.serialize
  }

  trait BoundMonitor extends Bound with AkkaStreamModule.StreamMetricsDef[Metric[Long]] {
    def runningStreamsTotal: Histogram[Long]
    def streamActorsTotal: Histogram[Long]
    def streamProcessedMessages: MetricObserver[Long, Attributes]
  }

}
