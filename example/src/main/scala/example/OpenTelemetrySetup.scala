package example

import java.time.Duration

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.`export`.PeriodicMetricReader

import io.scalac.mesmer.extension.opentelemetry.OpenTelemetryProvider

class OpenTelemetrySetup extends OpenTelemetryProvider {

  override def create(): OpenTelemetry = {

    val metricExporter: OtlpGrpcMetricExporter = OtlpGrpcMetricExporter.getDefault

    val factory = PeriodicMetricReader
      .builder(metricExporter)
      .setInterval(Duration.ofSeconds(1))
      .newMetricReaderFactory()

    val meterProvider: SdkMeterProvider = SdkMeterProvider
      .builder()
      .registerMetricReader(factory)
      .build()

    val openTelemetry = OpenTelemetrySdk
      .builder()
      .setMeterProvider(meterProvider)
      .buildAndRegisterGlobal()

    sys.addShutdownHook(meterProvider.shutdown())

    openTelemetry
  }
}
