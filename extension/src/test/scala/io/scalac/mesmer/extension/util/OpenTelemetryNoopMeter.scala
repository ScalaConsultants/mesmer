package io.opentelemetry.api.metrics

object OpenTelemetryNoopMeter {

  lazy val instance: Meter = DefaultMeter.getInstance()
}
