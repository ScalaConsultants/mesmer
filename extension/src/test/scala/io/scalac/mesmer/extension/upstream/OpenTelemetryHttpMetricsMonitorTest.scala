package io.scalac.mesmer.extension.upstream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.extension.metric.HttpMetricsMonitor.Attributes
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopCounter
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopLongHistogram
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedCounter
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedHistogram
import io.scalac.mesmer.extension.util.OpenTelemetryNoopMeter

class OpenTelemetryHttpMetricsMonitorTest extends AnyFlatSpec with Matchers {
  behavior of "OpenTelemetryHttpConnectionMetricsMonitor"

  val TestLabels: Attributes = Attributes(None, "/test", "GET", "200")

  private def config(value: Boolean) = AkkaHttpModule.Impl(
    requestTime = value,
    requestCounter = value,
    connections = value
  )

  it should "bind to OpenTelemetry instruments if metric is enabled" in {
    val sut = new OpenTelemetryHttpMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryHttpMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)

    bound.requestTime should be(a[WrappedHistogram])
    bound.requestCounter should be(a[WrappedCounter])

  }

  it should "bind to noop instruments if metric is disabled" in {
    val sut = new OpenTelemetryHttpMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryHttpMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)
    bound.requestTime should be(a[NoopLongHistogram.type])
    bound.requestCounter should be(a[NoopCounter.type])
  }
}
