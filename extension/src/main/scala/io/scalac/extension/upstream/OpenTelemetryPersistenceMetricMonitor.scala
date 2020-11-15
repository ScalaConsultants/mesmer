package io.scalac.extension.upstream

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.common.Labels
import io.scalac.extension.metric.Metric._
import io.scalac.extension.metric.{ MetricRecorder, PersistenceMetricMonitor }
import io.scalac.extension.model._

class OpenTelemetryPersistenceMetricMonitor(instrumentationName: String) extends PersistenceMetricMonitor {
  import PersistenceMetricMonitor._
  private val recoveryTimeRecorder = OpenTelemetry
    .getMeter(instrumentationName)
    .longValueRecorderBuilder("recovery_time")
    .setDescription("Amount of time needed for entity recovery")
    .build()

  override def bind(path: Path): BoundMonitor =
    new BoundMonitor {

      override lazy val recoveryTime: MetricRecorder[Long] =
        recoveryTimeRecorder.bind(Labels.of("path", path)).toMetricRecorder()
    }
}
