package io.scalac.mesmer.extension.upstream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaHttpModule
import io.scalac.mesmer.extension.metric.HttpConnectionMetricsMonitor.Attributes
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopUpDownCounter
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedUpDownCounter
import io.scalac.mesmer.extension.util.OpenTelemetryNoopMeter

class OpenTelemetryHttpConnectionMetricsMonitorTest extends AnyFlatSpec with Matchers {

  behavior of "OpenTelemetryHttpConnectionMetricsMonitor"

  val TestAttributes: Attributes = Attributes(None, "localhost", 0)

  private def config(value: Boolean) = AkkaHttpModule.Impl(
    requestTime = value,
    requestCounter = value,
    connections = value
  )

  it should "bind to OpenTelemetry instruments if metric is enabled" in {
    val sut = new OpenTelemetryHttpConnectionMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryHttpConnectionMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestAttributes)

    bound.connections should be(a[WrappedUpDownCounter])

  }

  it should "bind to noop instruments if metric is disabled" in {
    val sut = new OpenTelemetryHttpConnectionMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryHttpConnectionMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestAttributes)
    bound.connections should be(a[NoopUpDownCounter.type])
  }
}
