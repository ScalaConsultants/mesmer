package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.model._

object StreamMetricMonitor {

  case class Labels(
    node: Option[Node],
    stream: String
  ) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val required: Seq[String] = Seq("stream", stream)
      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional ++ required: _*)
    }
  }

  trait BoundMonitor extends Bound {
    def runningStreams: MetricRecorder[Long]
  }

}
