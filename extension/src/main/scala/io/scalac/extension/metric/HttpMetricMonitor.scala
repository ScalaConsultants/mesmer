package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.metric.HttpMetricMonitor._
import io.scalac.extension.model._

object HttpMetricMonitor {


  final case class Labels(path: Path, method: Method) {
    def toOpenTelemetry: OpenTelemetryLabels = OpenTelemetryLabels.of("path", path, "method", method)
  }
}

trait HttpMetricMonitor extends Bindable[Labels] {
  import HttpMetricMonitor._

  override type Bound <: BoundMonitor

  override def bind(labels: Labels): Bound

  trait BoundMonitor extends Synchronized {
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: UpCounter[Long] with Instrument[Long]
  }
}
