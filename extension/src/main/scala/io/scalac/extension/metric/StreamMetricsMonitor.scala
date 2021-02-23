package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.model._

object StreamMetricsMonitor {

  case class Labels(
    node: Option[Node],
    stream: Option[String],
    operation: String,
    direction: String,
    connectionWith: String
  ) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val required: Seq[String] = Seq("operation", operation, "direction", direction, "connectionWith", connectionWith)
      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty) ++ stream.map(n => Seq("stream", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional ++ required: _*)
    }
  }

  trait BoundMonitor extends Bound {
    def operatorProcessedMessages: UpCounter[Long]
    def runningStreams: MetricRecorder[Long]
  }

}
