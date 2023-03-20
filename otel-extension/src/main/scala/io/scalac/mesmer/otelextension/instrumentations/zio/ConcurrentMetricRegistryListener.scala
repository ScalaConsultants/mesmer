package io.scalac.mesmer.otelextension.instrumentations.zio

import java.util.concurrent.ConcurrentHashMap

import io.scalac.mesmer.otelextension.instrumentations.zio.ZIOMetrics.DoubleHistogram
import zio.Unsafe
import zio.metrics.MetricKey
import zio.metrics.MetricKeyType

import scala.jdk.CollectionConverters._

class ConcurrentMetricRegistryListener(client: ConcurrentMetricRegistryClient, zioMetrics: ZIOMetrics) {

  client.addListener(
    new ConcurrentMetricRegistryClient.MetricListener {
      private val instruments =
        new ConcurrentHashMap[MetricKey[MetricKeyType.Histogram], DoubleHistogram]().asScala

      override def updateHistogram(key: MetricKey[MetricKeyType.Histogram], value: Double)(implicit
        unsafe: Unsafe
      ): Unit = {
        val instrument = instruments
          .getOrElseUpdate(key, zioMetrics.registerHistogramSyncMetric(key))
        instrument.record(value, key.tags)
      }
    }
  )
}
