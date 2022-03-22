package io.scalac.mesmer.agent.akka.persistence

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
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

  val persistentEventTotalCounter: LongCounter = meter
    .counterBuilder("persistence.new.event.count")
    .setDescription("Amount of persist events")
    .build()

  val persistentEventRecorder: LongHistogram = meter
    .histogramBuilder("persistence.new.event.recorder")
    .ofLongs()
    .setDescription("Amount of time needed for entity to persist events")
    .build()

  val recoveryTimeRecorder: LongHistogram = meter
    .histogramBuilder("persistence.new.recovery.recorder")
    .ofLongs()
    .setDescription("Amount of time needed for entity recovery")
    .build()

}
