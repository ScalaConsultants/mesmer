package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model.Tag.StreamName
import io.scalac.core.model._

object StreamMetricMonitor {

  case class EagerLabels(
    node: Option[Node]
  ) extends LabelSerializable {
    override lazy val serialize: RawLabels = node.serialize
  }

  case class Labels(node: Option[Node], streamName: StreamName) extends LabelSerializable {
    override lazy val serialize: RawLabels = node.serialize ++ streamName.serialize
  }

  trait BoundMonitor extends Bound {
    def runningStreamsTotal: MetricRecorder[Long]
    def streamActorsTotal: MetricRecorder[Long]
    def streamProcessedMessages: LazyMetricObserver[Long, Labels]
  }

}
