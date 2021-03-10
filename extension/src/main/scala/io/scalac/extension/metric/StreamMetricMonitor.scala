package io.scalac.extension.metric

import io.scalac.core.model._

object StreamMetricMonitor {

  final case class Labels(node: Option[Node])

  implicit val streamMetricsLabelSerializer: LabelSerializer[Labels] = labels => {
    labels.node.serialize
  }

  trait BoundMonitor extends Bound {
    def runningStreams: MetricObserver[Long]
    def streamActors: MetricObserver[Long]
  }

}
