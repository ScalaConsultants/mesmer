package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter

object Instruments {

  lazy val receivedMessages: LongCounter = GlobalOpenTelemetry
    .getMeter("mesmer")
    .counterBuilder("received_messages")
    .build()

}
