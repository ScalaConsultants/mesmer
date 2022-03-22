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

  val recoveryTotalCounter: LongCounter = meter
    .counterBuilder("persistence.new.recovery.count")
    .setDescription("Amount of recoveries")
    .build()

  lazy val persistentEventTotalCounter: LongCounter = meter
    .counterBuilder("persistence.new.event.count")
    .setDescription("Amount of persist events")
    .build()
}
