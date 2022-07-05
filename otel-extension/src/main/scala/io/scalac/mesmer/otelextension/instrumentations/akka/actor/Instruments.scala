package io.scalac.mesmer.otelextension.instrumentations.akka.actor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.MeterProvider

trait Instruments {

  def failedMessages: LongCounter

  def processingTime: LongHistogram

  def unhandled: LongCounter

  def dropped: LongCounter

  def mailboxTime: LongHistogram

  def stashed: LongCounter

  def sent: LongCounter
}

object Instruments {
  def apply(provider: MeterProvider): Instruments = new Instruments {

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
}

object InstrumentsProvider {
  @volatile
  private var _instruments: Instruments = _

  def instance(): Instruments =
    if (_instruments eq null) {
      setInstruments(Instruments(GlobalOpenTelemetry.get().getMeterProvider))
    } else _instruments

  private[mesmer] def setInstruments(instruments: Instruments): Instruments =
    synchronized {
      if (_instruments eq null) {
        _instruments = instruments
      }
      instruments
    }

}
