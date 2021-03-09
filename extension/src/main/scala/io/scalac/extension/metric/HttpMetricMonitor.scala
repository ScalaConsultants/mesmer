package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.core.model._

object HttpMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, method: Method) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val required: Seq[String] = Seq("path", path, "method", method)
      val optional              = node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional ++ required: _*)
    }
  }

  trait BoundMonitor extends Synchronized with Bound {
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: UpCounter[Long] with Instrument[Long]
  }

}
