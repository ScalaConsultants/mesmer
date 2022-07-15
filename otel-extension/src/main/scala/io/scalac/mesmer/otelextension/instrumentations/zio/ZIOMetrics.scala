package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter
import zio.Unsafe
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.Metric.Gauge
import zio.metrics.MetricState

object ZIOMetrics {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val executionMetricName: String => String = (suffix: String) => s"mesmer_zio_forwarded_$suffix"

  def registerCounterAsyncMetric(zioMetricName: String, counter: Counter[_]): Unit =
    meter
      .counterBuilder(executionMetricName(zioMetricName))
      .ofDoubles()
      .buildWithCallback(_.record(unsafeGetCount(counter)))

  def registerGaugeAsyncMetric(zioMetricName: String, counter: Gauge[_]): Unit =
    meter
      .gaugeBuilder(executionMetricName(zioMetricName))
      .buildWithCallback(_.record(unsafeGetGaugeValue(counter)))

  private def unsafeGetValue[S <: MetricState[_], M <: Metric[_, _, S]](metric: M): S =
    Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(metric.value).getOrThrowFiberFailure())

  private def unsafeGetGaugeValue(gauge: Gauge[_]): Double =
    unsafeGetValue[MetricState.Gauge, Gauge[_]](gauge).value

  private def unsafeGetCount(counter: Counter[_]): Double =
    unsafeGetValue[MetricState.Counter, Counter[_]](counter).count
}
