package io.scalac.mesmer.extension.config

import io.opentelemetry.api.metrics.GlobalMetricsProvider
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.MeterProvider

object InstrumentationLibrary {

  private final val name = "scalac_akka_metrics"

  val meterProvider: MeterProvider = GlobalMetricsProvider.get()
  val meter: Meter                 = meterProvider.get(name)

}
