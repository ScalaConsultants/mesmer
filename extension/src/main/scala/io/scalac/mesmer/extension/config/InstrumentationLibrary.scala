package io.scalac.mesmer.extension.config

import io.opentelemetry.api.metrics.GlobalMeterProvider
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.MeterProvider

object InstrumentationLibrary {

  private final val name = "mesmer-akka"

  def meterProvider: MeterProvider = GlobalMeterProvider.get()
  def mesmerMeter: Meter           = meterProvider.get(name)

}
