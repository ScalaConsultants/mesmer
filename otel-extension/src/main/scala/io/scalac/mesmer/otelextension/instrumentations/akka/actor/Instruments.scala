package io.scalac.mesmer.otelextension.instrumentations.akka.actor
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.{LongCounter, LongHistogram, LongUpDownCounter, MeterProvider}

trait Instruments {

  def failedMessages: LongCounter

  def processingTime: LongHistogram

  def unhandledMessages: LongCounter

  def droppedMessages: LongCounter

  def mailboxTime: LongHistogram

  def mailboxSize: LongUpDownCounter

  def stashedMessages: LongCounter

  def sentMessages: LongCounter

  def actorsCreated: LongCounter

  def actorsTerminated: LongCounter
}

object Instruments {
  def apply(provider: MeterProvider): Instruments = new Instruments {

    lazy val failedMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_failed_messages_total")
      .build()

    lazy val processingTime: LongHistogram = provider
      .get("mesmer")
      .histogramBuilder("mesmer_akka_actor_message_processing_time")
      .ofLongs()
      .build()

    lazy val unhandledMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_unhandled_messages_total")
      .build()

    lazy val droppedMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_dropped_messages_total")
      .build()

    lazy val mailboxTime: LongHistogram = provider
      .get("mesmer")
      .histogramBuilder("mesmer_akka_actor_mailbox_time")
      .ofLongs()
      .build()

    lazy val mailboxSize: LongUpDownCounter = provider
      .get("mesmer")
      .upDownCounterBuilder("mesmer_akka_actor_mailbox_size")
      .build()

    lazy val stashedMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_stashed_messages_total")
      .build()

    lazy val sentMessages: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_sent_messages_total")
      .build()

    lazy val actorsCreated: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_actors_created_total")
      .setDescription("Amount of actors created measured from Actor System start")
      .build()

    lazy val actorsTerminated: LongCounter = provider
      .get("mesmer")
      .counterBuilder("mesmer_akka_actor_actors_terminated_total")
      .setDescription("Amount of actors terminated measured from Actor System start")
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
