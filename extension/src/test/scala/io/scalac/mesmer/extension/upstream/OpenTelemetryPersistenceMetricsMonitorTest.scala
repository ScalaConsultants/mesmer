package io.scalac.mesmer.extension.upstream

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import io.scalac.mesmer.core.module.AkkaPersistenceModule
import io.scalac.mesmer.extension.metric.PersistenceMetricsMonitor.Attributes
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopCounter
import io.scalac.mesmer.extension.upstream.opentelemetry.NoopLongValueRecorder
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedCounter
import io.scalac.mesmer.extension.upstream.opentelemetry.WrappedLongValueRecorder
import io.scalac.mesmer.extension.util.OpenTelemetryNoopMeter

class OpenTelemetryPersistenceMetricsMonitorTest extends AnyFlatSpec with Matchers {
  behavior of "OpenTelemetryHttpConnectionMetricsMonitor"

  val TestLabels: Attributes = Attributes(None, "/", "")

  private def config(value: Boolean) = AkkaPersistenceModule.Impl(
    recoveryTime = value,
    recoveryTotal = value,
    persistentEvent = value,
    persistentEventTotal = value,
    snapshot = value
  )

  it should "bind to OpenTelemetry instruments if metric is enabled" in {
    val sut = new OpenTelemetryPersistenceMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(true),
      OpenTelemetryPersistenceMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)

    bound.recoveryTime should be(a[WrappedLongValueRecorder])
    bound.persistentEvent should be(a[WrappedLongValueRecorder])
    bound.recoveryTotal should be(a[WrappedCounter])
    bound.persistentEventTotal should be(a[WrappedCounter])
    bound.snapshot should be(a[WrappedCounter])
  }

  it should "bind to noop instruments if metric is disabled" in {
    val sut = new OpenTelemetryPersistenceMetricsMonitor(
      OpenTelemetryNoopMeter.instance,
      config(false),
      OpenTelemetryPersistenceMetricsMonitor.MetricNames.defaultConfig
    )

    val bound = sut.bind(TestLabels)

    bound.recoveryTime should be(a[NoopLongValueRecorder.type])
    bound.persistentEvent should be(a[NoopLongValueRecorder.type])
    bound.recoveryTotal should be(a[NoopCounter.type])
    bound.persistentEventTotal should be(a[NoopCounter.type])
    bound.snapshot should be(a[NoopCounter.type])
  }

}
