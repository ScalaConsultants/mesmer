package io.scalac.mesmer.extension.upstream

import io.opentelemetry.api.metrics.OpenTelemetryNoopMeter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor.EagerAttributes
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopLongValueRecorder
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedLongValueRecorder
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedMetricObserver

class OpenTelemetryStreamMetricsMonitorTest extends AnyFlatSpec with Matchers {

  private def config(value: Boolean) = AkkaStreamModule.Impl(
    runningStreamsTotal = value,
    streamActorsTotal = value,
    streamProcessedMessages = value,
    processedMessages = value,
    operators = value,
    demand = value
  )

  val testAttributes: EagerAttributes = EagerAttributes(None)

  it should "bind to OpenTelemetry instruments if metric is enabled" in {

    val sut = new OpenTelemetryStreamMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryStreamMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(testAttributes)

    bound.runningStreamsTotal should be(a[WrappedLongValueRecorder])
    bound.streamActorsTotal should be(a[WrappedLongValueRecorder])
    bound.streamProcessedMessages should be(a[WrappedMetricObserver[_, _]])
  }

  it should "bind to noop instruments if metric is disabled" in {

    val sut = new OpenTelemetryStreamMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryStreamMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(testAttributes)

    bound.runningStreamsTotal should be(a[NoopLongValueRecorder.type])
    bound.streamActorsTotal should be(a[NoopLongValueRecorder.type])
    bound.streamProcessedMessages should be(a[MetricObserver.NoopMetricObserver.type])
  }
}
