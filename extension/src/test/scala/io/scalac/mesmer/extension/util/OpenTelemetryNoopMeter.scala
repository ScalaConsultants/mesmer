package io.opentelemetry.api.metrics

import io.opentelemetry.api.metrics.internal.NoopMeterProvider

object OpenTelemetryNoopMeter {

  lazy val instance: Meter = NoopMeterProvider.getInstance().meterBuilder("tests").build()
}
