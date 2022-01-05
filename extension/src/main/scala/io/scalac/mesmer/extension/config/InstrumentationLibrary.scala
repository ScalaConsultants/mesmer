package io.scalac.mesmer.extension.config

import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.MeterProvider
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader

import scala.concurrent.duration.Duration
import scala.jdk.DurationConverters.ScalaDurationOps

object InstrumentationLibrary {

  private final val name = "mesmer-akka"

  // TODO (switched this completely to enforce the SdkMeterProvider)
  // FIXME: shouldn't it be the default way to go rather than using the GlobalMeterProvider (which is noop?)
  def nonGlobalMeterProvider(): MeterProvider = {
    val metricExporter: OtlpGrpcMetricExporter = OtlpGrpcMetricExporter.getDefault

    val factory = PeriodicMetricReader.create(metricExporter, Duration(5, "second").toJava)

    SdkMeterProvider
      .builder()
      .registerMetricReader(factory)
      .buildAndRegisterGlobal()
  }

  //def meterProvider: MeterProvider = GlobalMeterProvider.get()

  def meterProvider: MeterProvider = nonGlobalMeterProvider()

  def mesmerMeter: Meter = {
    println("MESMERMETER")
    meterProvider.get(name)
  }

}
