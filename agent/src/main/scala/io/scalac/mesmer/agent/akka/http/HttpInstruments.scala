package io.scalac.mesmer.agent.akka.http

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.LongUpDownCounter
import io.opentelemetry.api.metrics.Meter

object HttpInstruments {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer-akka")

  val connectionTotalCounter: LongUpDownCounter = meter
    .upDownCounterBuilder("new.http.connections.counter")
    .setDescription("Amount of connections")
    .build()

  val requestTimeRequest: LongHistogram = meter
    .histogramBuilder("new.http.request.time.recorder")
    .ofLongs()
    .setDescription("Amount of ms request took to complete")
    .build()

  val requestTotalCounter: LongCounter = meter
    .counterBuilder("new.http.request.counter")
    .setDescription("Amount of requests")
    .build()
}
