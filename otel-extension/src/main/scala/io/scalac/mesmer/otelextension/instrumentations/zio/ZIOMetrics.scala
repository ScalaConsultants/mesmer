package io.scalac.mesmer.otelextension.instrumentations.zio
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import zio.Unsafe
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.Metric.Gauge
import zio.metrics.MetricLabel
import zio.metrics.MetricState

object ZIOMetrics {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val metricName: String => String = (suffix: String) => s"mesmer_zio_forwarded_$suffix"

  def registerCounterAsyncMetric(zioMetricName: String, counter: Counter[_], attributes: Attributes): Unit =
    // println("FINDME 2")
    // println(counter)
    // Thread.currentThread().getStackTrace().map(st => println(s"${st.getFileName()}:${st.getLineNumber()} - ${st.getMethodName()}"))
    meter
      .counterBuilder(metricName(zioMetricName))
      .ofDoubles()
      .buildWithCallback(_.record(unsafeGetCount(counter), attributes))

  def registerGaugeAsyncMetric(zioMetricName: String, counter: Gauge[_], attributes: Attributes): Unit =
    meter
      .gaugeBuilder(metricName(zioMetricName))
      .buildWithCallback(_.record(unsafeGetGaugeValue(counter), attributes))

  def buildAttributes(metricLabels: Set[MetricLabel]): Attributes = {
    val builder = Attributes.builder()
    metricLabels.foreach { case MetricLabel(key, value) => builder.put(AttributeKey.stringKey(key), value) }
    builder.build()
  }

  private def unsafeGetValue[S <: MetricState[_], M <: Metric[_, _, S]](metric: M): S =
    Unsafe.unsafe(implicit u => zio.Runtime.default.unsafe.run(metric.value).getOrThrowFiberFailure())

  private def unsafeGetGaugeValue(gauge: Gauge[_]): Double =
    unsafeGetValue[MetricState.Gauge, Gauge[_]](gauge).value

  private def unsafeGetCount(counter: Counter[_]): Double =
    unsafeGetValue[MetricState.Counter, Counter[_]](counter).count

}
