package io.scalac.extension.config

import io.opentelemetry.api.metrics.GlobalMetricsProvider
import io.opentelemetry.sdk.metrics.SdkMeterProvider

object InstrumentationLibrary {

  private final val name = "scalac_akka_metrics"

  final val meterProvider = GlobalMetricsProvider.get().asInstanceOf[SdkMeterProvider]
  final val meterProducer = meterProvider.getMetricProducer
  final val meter         = meterProvider.get(name)

}
