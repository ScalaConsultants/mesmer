package io.scalac.mesmer.otelextension.instrumentations.akka.actor
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.MeterProvider

import io.scalac.mesmer.core.actor.ActorRefConfiguration

// TODO can we add resources here to automatically inject actor system tags?
final class Instruments(val config: ActorRefConfiguration, provider: MeterProvider) {

  lazy val failedMessages: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_failed_total")
    .build()

  lazy val processingTime: LongHistogram = provider
    .get("mesmer")
    .histogramBuilder("mesmer_akka_processing_time")
    .ofLongs()
    .build()

  lazy val unhandled: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_unhandled_total")
    .build()

  lazy val dropped: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_dropped_total")
    .build()

  lazy val mailboxTime: LongHistogram = provider
    .get("mesmer")
    .histogramBuilder("mesmer_akka_mailbox_time")
    .ofLongs()
    .build()

  lazy val stashed: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_stashed_total")
    .build()

  lazy val sent: LongCounter = provider
    .get("mesmer")
    .counterBuilder("mesmer_akka_sent_total")
    .build()
}
