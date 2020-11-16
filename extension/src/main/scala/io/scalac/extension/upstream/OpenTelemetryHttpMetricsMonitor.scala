package io.scalac.extension.upstream

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.common.Labels
import io.scalac.extension.metric.Metric._
import io.scalac.extension.metric.{ HttpMetricMonitor, MetricRecorder }
import io.scalac.extension.model._

class OpenTelemetryHttpMetricsMonitor(instrumentationName: String) extends HttpMetricMonitor {
  import HttpMetricMonitor.BoundMonitor

  private val requestTimeRequest = OpenTelemetry
    .getMeter(instrumentationName)
    .longValueRecorderBuilder("requestTime")
    .setDescription("Amount of ms request took to complete")
    .build()

  override def bind(path: Path, method: Method): HttpMetricMonitor.BoundMonitor = new BoundMonitor {
    override val requestTime: MetricRecorder[Long] =
      requestTimeRequest.bind(Labels.of("path", path, "method", method)).toMetricRecorder()
  }
}
