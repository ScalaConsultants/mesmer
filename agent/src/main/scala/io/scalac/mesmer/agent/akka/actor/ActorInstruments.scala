package io.scalac.mesmer.agent.akka.actor

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

object ActorInstruments {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer-akka")

  val messagesReceivedCounter: LongCounter = meter
    .counterBuilder("received-messages")
    .setDescription("new way of counting actor's received messages")
    .setUnit("messages")
    .build()

}
