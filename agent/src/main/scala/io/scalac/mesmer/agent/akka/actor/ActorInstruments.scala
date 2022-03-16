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

  val sentMessagesCounter: LongCounter = meter
    .counterBuilder("sent-messages")
    .setDescription("new way of counting actor's sent messages")
    .setUnit("messages")
    .build()

  val unhandledMessages: LongCounter = meter
    .counterBuilder("unhandled-messages")
    .setDescription("new way of counting actor's unhandled messages")
    .setUnit("messages")
    .build()

}
