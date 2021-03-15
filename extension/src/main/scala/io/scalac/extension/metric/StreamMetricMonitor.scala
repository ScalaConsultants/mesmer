package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object StreamMetricMonitor {

  final case class Labels(node: Option[Node]) extends LabelSerializable {
    override def serialize: RawLabels = node.serialize
  }

  trait BoundMonitor extends Bound {
    def runningStreams: MetricObserver[Long]
    def streamActors: MetricObserver[Long]
  }

}
