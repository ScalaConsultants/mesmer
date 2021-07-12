package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model.Tag.StreamName
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaStreamModule

object StreamMetricsMonitor {

  final case class EagerLabels(
    node: Option[Node]
  ) extends LabelSerializable {
    lazy val serialize: RawLabels = node.serialize
  }

  final case class Labels(node: Option[Node], streamName: StreamName) extends LabelSerializable {
    lazy val serialize: RawLabels = node.serialize ++ streamName.serialize
  }

  trait BoundMonitor extends Bound with AkkaStreamModule.StreamMetricsDef[Metric[Long]] {
    def runningStreamsTotal: MetricRecorder[Long]
    def streamActorsTotal: MetricRecorder[Long]
    def streamProcessedMessages: MetricObserver[Long, Labels]
  }

}
