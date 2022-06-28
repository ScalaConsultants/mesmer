package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram

trait Instruments {

  def recovery: LongHistogram

  def persistentEvent: LongHistogram

  def snapshot: LongCounter

}

object InstrumentsProvider {

  @volatile
  private var _instance: Instruments = _

  def instance(): Instruments =
    if (_instance eq null) {
      val provider = GlobalOpenTelemetry.get().getMeterProvider

      setInstruments(new Instruments {
        val recovery: LongHistogram = provider
          .get("mesmer")
          .histogramBuilder("persistence_recovery")
          .ofLongs()
          .build()

        val persistentEvent: LongHistogram = provider
          .get("mesmer")
          .histogramBuilder("persistence_event")
          .ofLongs()
          .build()

        val snapshot: LongCounter = provider
          .get("mesmer")
          .counterBuilder("persistence_snapshot")
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
