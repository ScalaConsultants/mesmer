package io.scalac.mesmer.otelextension.instrumentations.zio

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.MetricLabel

class ZIOMetrics(client: ConcurrentMetricRegistryClient) {
  import ZIOMetrics._

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val metricName: String => String = (suffix: String) => s"mesmer_zio_forwarded_$suffix"

  def registerCounterAsyncMetric(metricKey: MetricKey.Counter): AutoCloseable =
    meter
      .counterBuilder(metricName(metricKey.name))
      .ofDoubles()
      .buildWithCallback(_.record(unsafeGetCounterValue(metricKey), buildAttributes(metricKey.tags)))

  def registerGaugeAsyncMetric(metricKey: MetricKey.Gauge): AutoCloseable =
    meter
      .gaugeBuilder(metricName(metricKey.name))
      .buildWithCallback(_.record(unsafeGetGaugeValue(metricKey), buildAttributes(metricKey.tags)))

  def registerHistogramSyncMetric(metricKey: MetricKey.Histogram): DoubleHistogram = {
    val underlying = meter
      .histogramBuilder(metricName(metricKey.name))
      .build()
    // returning DoubleHistogram returned by histogramBuilder.build() causes class not found errors in advices,
    // this is a workaround
    val fn1 = (double: Double) => underlying.record(double)
    val fn2 = (double: Double, tags: Set[MetricLabel]) => underlying.record(double, buildAttributes(tags))
    new DoubleHistogram {
      def record(double: Double): Unit                         = fn1(double)
      def record(double: Double, tags: Set[MetricLabel]): Unit = fn2(double, tags)
    }
  }

  private def buildAttributes(metricLabels: Set[MetricLabel]): Attributes = {
    val builder = Attributes.builder()
    metricLabels.foreach { case MetricLabel(key, value) => builder.put(AttributeKey.stringKey(key), value) }
    builder.build()
  }

  private def unsafeGetCounterValue(metricKey: MetricKey[MetricKeyType.Counter]): Double =
    client.get(metricKey).get().count

  private def unsafeGetGaugeValue(metricKey: MetricKey[MetricKeyType.Gauge]): Double =
    client.get(metricKey).get().value
}

object ZIOMetrics {
  trait DoubleHistogram {
    def record(double: Double): Unit
    def record(double: Double, tags: Set[MetricLabel]): Unit
  }
}
