package io.scalac.extension.config

import io.opentelemetry.api.metrics.{ GlobalMetricsProvider, Meter, MeterProvider }

object InstrumentationLibrary {

  private final val name = "scalac_akka_metrics"

  val meterProvider: MeterProvider = GlobalMetricsProvider.get()
  val meter: Meter                 = meterProvider.get(name)

}
