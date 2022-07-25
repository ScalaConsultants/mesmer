package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram

trait Instruments {

  def recoveryTime: LongHistogram

  def persistentEventTime: LongHistogram

  def snapshots: LongCounter

}

object InstrumentsProvider {

  @volatile
  private var _instance: Instruments = _

  def instance(): Instruments =
    if (_instance eq null) {
      val provider = GlobalOpenTelemetry.get().getMeterProvider

      setInstruments(new Instruments {
        val recoveryTime: LongHistogram = provider
          .get("mesmer")
          .histogramBuilder("mesmer_akka_persistence_recovery_time")
          .ofLongs()
          .build()

        val persistentEventTime: LongHistogram = provider
          .get("mesmer")
          .histogramBuilder("mesmer_akka_persistence_event_time")
          .ofLongs()
          .build()

        val snapshots: LongCounter = provider
          .get("mesmer")
          .counterBuilder("mesmer_akka_persistence_event_total")
          .build()
      })
    } else _instance

  def setInstruments(instruments: Instruments): Instruments =
    synchronized {
      if (_instance eq null) {
        _instance = instruments
      }
      _instance
    }
}
