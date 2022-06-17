package io.scalac.mesmer.otelextension.instrumentations.akka.actor
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.MeterProvider

import io.scalac.mesmer.core.actor.ActorRefConfiguration

// TODO can we add resources here to automatically inject actor system tags?
final class Instruments(val config: ActorRefConfiguration, provider: MeterProvider) {

  lazy val failedMessages: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_failed_messages")
    .build()

  lazy val processingTime: LongHistogram = provider
    .get("mesmer")
    .histogramBuilder("mesmer_akka_processing_time")
    .ofLongs()
    .build()
}
