package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter
import zio.Unsafe
import zio.metrics.Metric.Counter

object ZIOMetrics {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val executionMetricName: String => String = (suffix: String) => s"mesmer_zio_forwarded_$suffix"

  def registerCounterAsyncMetric(zioMetricName: String, counter: Counter[_]): Unit =
    meter
      .counterBuilder(executionMetricName(zioMetricName))
      .ofDoubles()
      .buildWithCallback(_.record(unsafeGetCount(counter)))

  private def unsafeGetCount(metric: Counter[_]): Double =
    Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(metric.value).getOrThrowFiberFailure().count)
}
