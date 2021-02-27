package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.model._

object StreamMetricMonitor {

  case class Labels(
    node: Option[Node]
  ) {
    def toOpenTelemetry: OpenTelemetryLabels = {

      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional: _*)
    }
  }

  trait BoundMonitor extends Bound {
    def runningStreams: MetricObserver[Long]
    def streamActors: MetricObserver[Long]
  }

}
