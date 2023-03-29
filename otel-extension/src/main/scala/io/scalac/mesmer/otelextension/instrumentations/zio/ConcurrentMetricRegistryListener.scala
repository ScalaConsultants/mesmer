package io.scalac.mesmer.otelextension.instrumentations.zio

import java.util.concurrent.ConcurrentHashMap

import io.opentelemetry.api.metrics.DoubleCounter
import io.opentelemetry.api.metrics.DoubleHistogram
import zio.Unsafe
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType
import zio.metrics.MetricLabel

import scala.jdk.CollectionConverters._

class ConcurrentMetricRegistryListener(client: ConcurrentMetricRegistryClient, zioMetrics: ZIOMetrics) {

  client.addListener(
    new ConcurrentMetricRegistryClient.MetricListener {
      private val histograms =
        new ConcurrentHashMap[MetricKey[MetricKeyType.Histogram], DoubleHistogram]().asScala

      private val frequencies =
        new ConcurrentHashMap[MetricKey[MetricKeyType.Frequency], DoubleCounter]().asScala

      override def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit
        unsafe: Unsafe
      ): Unit = {
        val histogram = histograms
          .getOrElseUpdate(key, zioMetrics.registerHistogramSyncMetric(key))
        histogram.record(value, zioMetrics.buildAttributes(key.tags))
      }

      override def updateFrequency(key: MetricKey[MetricKeyType.Frequency], value: String)(implicit
        unsafe: Unsafe
      ): Unit = {
        val frequency = frequencies
          .getOrElseUpdate(key, zioMetrics.registerFrequencySyncMetric(key))
        frequency.add(
          1,
          zioMetrics.buildAttributes(
            key.tags + MetricLabel("bucket", value)
          ) // `bucket` label is used as discriminator by zio-metrics-connectors Prometheus implementation
        )
      }
    }
  )
}
