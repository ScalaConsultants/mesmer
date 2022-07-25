package io.scalac.mesmer.otelextension.instrumentations.akka.actor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import io.opentelemetry.api.metrics.MeterProvider

trait Instruments {

  def failedMessages: LongCounter

  def processingTime: LongHistogram

  def unhandledMessages: LongCounter

  def droppedMessages: LongCounter

  def mailboxTime: LongHistogram

  def stashedMessages: LongCounter

  def sentMessages: LongCounter
}

object Instruments {
  def apply(provider: MeterProvider): Instruments = new Instruments {

    lazy val failedMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_failed_messages_total")
      .build()

    lazy val processingTime: LongHistogram = provider
      .get("mesmer")
      .histogramBuilder("mesmer_akka_message_processing_time")
      .ofLongs()
      .build()

    lazy val unhandledMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_unhandled_messages_total")
      .build()

    lazy val droppedMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_dropped_total")
      .build()

    lazy val mailboxTime: LongHistogram = provider
      .get("mesmer")
      .histogramBuilder("mesmer_akka_mailbox_time")
      .ofLongs()
      .build()

    lazy val stashedMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_stashed_messages_total")
      .build()

    lazy val sentMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_sent_messages_total")
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
