package io.scalac.mesmer.zio.metrics

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableDoubleCounter
import io.opentelemetry.api.metrics.ObservableDoubleGauge
import zio.MetricsRegistryClient
import zio.Unsafe
import zio.metrics.Metric
import zio.metrics.Metric.Counter
import zio.metrics.Metric.Gauge
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.MetricLabel
import zio.metrics.MetricState

object ZIOMetrics {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val metricName: String => String = (suffix: String) => s"mesmer_zio_forwarded_$suffix"

  def registerCounterAsyncMetric(metricKey: MetricKey[MetricKeyType.Counter]): ObservableDoubleCounter =
    meter
      .counterBuilder(metricName(metricKey.name))
      .ofDoubles()
      .buildWithCallback(_.record(unsafeGetCounterValue(metricKey), buildAttributes(metricKey.tags)))

  def registerGaugeAsyncMetric(metricKey: MetricKey[MetricKeyType.Gauge]): ObservableDoubleGauge =
    meter
      .gaugeBuilder(metricName(metricKey.name))
      .buildWithCallback(_.record(unsafeGetGaugeValue(metricKey), buildAttributes(metricKey.tags)))

  def buildAttributes(metricLabels: Set[MetricLabel]): Attributes = {
    val builder = Attributes.builder()
    metricLabels.foreach { case MetricLabel(key, value) => builder.put(AttributeKey.stringKey(key), value) }
    builder.build()
  }

  private def unsafeGetCounterValue(metricKey: MetricKey[MetricKeyType.Counter]): Double =
    MetricsRegistryClient.get(metricKey).get().count

  private def unsafeGetGaugeValue(metricKey: MetricKey[MetricKeyType.Gauge]): Double =
    MetricsRegistryClient.get(metricKey).get().value

}
