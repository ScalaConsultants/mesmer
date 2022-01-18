package io.scalac.mesmer.extension.upstream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaStreamModule
import io.scalac.mesmer.extension.metric.MetricObserver
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedMetricObserver
import io.scalac.mesmer.extension.util.OpenTelemetryNoopMeter

class OpenTelemetryStreamOperatorMetricsMonitorTest extends AnyFlatSpec with Matchers {

  private def config(value: Boolean) = AkkaStreamModule.Impl(
    runningStreamsTotal = value,
    streamActorsTotal = value,
    streamProcessedMessages = value,
    processedMessages = value,
    operators = value,
    demand = value
  )

  it should "bind to OpenTelemetry instruments if metric is enabled" in {

    val sut = new OpenTelemetryStreamOperatorMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryStreamOperatorMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind()

    bound.processedMessages should be(a[WrappedMetricObserver[_, _]])
    bound.operators should be(a[WrappedMetricObserver[_, _]])
    bound.demand should be(a[WrappedMetricObserver[_, _]])
  }

  it should "bind to noop instruments if metric is disabled" in {
    val sut = new OpenTelemetryStreamOperatorMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryStreamOperatorMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind()

    bound.processedMessages should be(a[MetricObserver.NoopMetricObserver.type])
    bound.operators should be(a[MetricObserver.NoopMetricObserver.type])
    bound.demand should be(a[MetricObserver.NoopMetricObserver.type])
  }
}
