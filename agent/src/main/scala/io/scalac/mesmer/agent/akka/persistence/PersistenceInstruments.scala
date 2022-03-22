package io.scalac.mesmer.agent.akka.persistence

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.Meter

object PersistenceInstruments {

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer-akka")

  val snapshotCounter: LongCounter = meter
    .counterBuilder("persistence.new.snapshot.count")
    .setDescription("Amount of snapshots created")
    .build()
}
