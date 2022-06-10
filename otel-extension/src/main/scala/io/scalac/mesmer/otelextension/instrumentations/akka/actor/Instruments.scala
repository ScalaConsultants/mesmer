package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram

object Instruments {

  lazy val failedMessages: LongCounter = GlobalOpenTelemetry
    .getMeter("mesmer")
    .counterBuilder("mesmer_akka_failed_messages")
    .build()

  lazy val processingTime: LongHistogram = GlobalOpenTelemetry
    .getMeter("mesmer")
    .histogramBuilder("mesmer_akka_processing_time")
    .ofLongs()
    .build()

}
