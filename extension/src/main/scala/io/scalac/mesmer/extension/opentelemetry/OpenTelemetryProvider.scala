package io.scalac.mesmer.extension.opentelemetry

import io.opentelemetry.api.OpenTelemetry

trait OpenTelemetryProvider {
  def create(): OpenTelemetry
}
