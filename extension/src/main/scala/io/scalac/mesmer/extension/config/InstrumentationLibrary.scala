package io.scalac.mesmer.extension.config

import io.opentelemetry.api.metrics.GlobalMeterProvider
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.MeterProvider

object InstrumentationLibrary {

  private final val name = "mesmer-akka"

  val meterProvider: MeterProvider = GlobalMeterProvider.get()
  val meter: Meter                 = meterProvider.get(name)

}
