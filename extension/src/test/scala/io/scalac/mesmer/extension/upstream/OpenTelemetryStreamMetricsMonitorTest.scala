package io.scalac.mesmer.extension.upstream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.metric.StreamMetricsMonitor.EagerAttributes
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopLongHistogram
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedHistogram
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedMetricObserver
import io.scalac.mesmer.extension.util.OpenTelemetryNoopMeter

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

    bound.runningStreamsTotal should be(a[WrappedHistogram])
    bound.streamActorsTotal should be(a[WrappedHistogram])
    bound.streamProcessedMessages should be(a[WrappedMetricObserver[_, _]])
  }

  it should "bind to noop instruments if metric is disabled" in {

    val sut = new OpenTelemetryStreamMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryStreamMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(testAttributes)

    bound.runningStreamsTotal should be(a[NoopLongHistogram.type])
    bound.streamActorsTotal should be(a[NoopLongHistogram.type])
    bound.streamProcessedMessages should be(a[MetricObserver.NoopMetricObserver.type])
  }
}
