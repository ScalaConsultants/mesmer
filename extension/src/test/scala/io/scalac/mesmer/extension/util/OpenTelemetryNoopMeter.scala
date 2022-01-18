package io.scalac.mesmer.extension.util

import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.MeterProvider

object OpenTelemetryNoopMeter {

  lazy val instance: Meter = MeterProvider.noop().meterBuilder("tests").build()
}
