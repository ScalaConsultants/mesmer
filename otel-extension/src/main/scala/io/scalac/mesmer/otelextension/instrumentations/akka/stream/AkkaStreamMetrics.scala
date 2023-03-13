package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.typed.ActorSystem
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongMeasurement

final class AkkaStreamMetrics(actorSystem: ActorSystem[_]) {
  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  @volatile private var runningStreamsTotal: Map[Attributes, Long]          = Map.empty
  @volatile private var runningActorsTotal: Map[Attributes, Long]           = Map.empty
  @volatile private var streamProcessedMessagesTotal: Map[Attributes, Long] = Map.empty
  @volatile private var runningOperators: Map[Attributes, Long]             = Map.empty
  @volatile private var operatorDemand: Map[Attributes, Long]               = Map.empty

  private val callback: (ObservableLongMeasurement, Map[Attributes, Long]) => Unit =
    (measurement: ObservableLongMeasurement, values: Map[Attributes, Long]) =>
      values.foreach { case (attributes, value) =>
        measurement.record(value, attributes)
      }

  meter
    .gaugeBuilder("mesmer_akka_streams_running_streams")
    .ofLongs()
    .setDescription("Streams running in the system")
    .buildWithCallback(callback(_, runningStreamsTotal))

  meter
    .gaugeBuilder("mesmer_akka_streams_actors")
    .setDescription("Actors running streams in the system")
    .ofLongs()
    .buildWithCallback(callback(_, runningActorsTotal))

  meter
    .counterBuilder("mesmer_akka_stream_processed_messages")
    .setDescription("Messages processed by whole stream")
    .buildWithCallback(callback(_, streamProcessedMessagesTotal))

  meter
    .counterBuilder("mesmer_akka_streams_running_operators")
    .setDescription("Operators in a system")
    .buildWithCallback(callback(_, runningOperators))

  meter
    .counterBuilder("mesmer_akka_streams_operator_demand")
    .setDescription("Messages demanded by operator")
    .buildWithCallback(callback(_, operatorDemand))

  def setRunningStreamsTotal(value: Long, attributes: Attributes): Unit = runningStreamsTotal = Map(attributes -> value)
  def setRunningActorsTotal(value: Long, attributes: Attributes): Unit  = runningActorsTotal = Map(attributes -> value)
  def setStreamProcessedMessagesTotal(values: Map[Attributes, Long]): Unit = streamProcessedMessagesTotal = values
  def setRunningOperators(values: Map[Attributes, Long]): Unit             = runningOperators = values
  def setOperatorDemand(values: Map[Attributes, Long]): Unit               = operatorDemand = values
}
