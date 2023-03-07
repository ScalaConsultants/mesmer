package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.typed.ActorSystem
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.AttributeKey.stringKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import io.opentelemetry.api.metrics.ObservableLongMeasurement

final class AkkaStreamMetrics(actorSystem: ActorSystem[_]) {
  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  @volatile private var runningStreamsTotal: Seq[(Long, Attributes)]          = Seq.empty
  @volatile private var runningActorsTotal: Seq[(Long, Attributes)]           = Seq.empty
  @volatile private var streamProcessedMessagesTotal: Seq[(Long, Attributes)] = Seq.empty
  @volatile private var runningOperators: Seq[(Long, Attributes)]             = Seq.empty
  @volatile private var operatorDemand: Seq[(Long, Attributes)]               = Seq.empty

  private val callback: (ObservableLongMeasurement, Seq[(Long, Attributes)]) => Unit =
    (measurement: ObservableLongMeasurement, values: Seq[(Long, Attributes)]) =>
      values.foreach { case (value, attributes) =>
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

  def setRunningStreamsTotal(value: Long, attributes: Attributes): Unit = runningStreamsTotal = Seq((value, attributes))
  def setRunningActorsTotal(values: Seq[(Long, Attributes)]): Unit      = runningActorsTotal = values
  def setStreamProcessedMessagesTotal(values: Seq[(Long, Attributes)]): Unit = streamProcessedMessagesTotal = values
  def setRunningOperators(values: Seq[(Long, Attributes)]): Unit             = runningOperators = values
  def setOperatorDemand(values: Seq[(Long, Attributes)]): Unit               = operatorDemand = values
}

object AkkaStreamMetrics {
  val streamNameAttributeKey: AttributeKey[String] = stringKey("stream_name")
  val stageNameAttributeKey: AttributeKey[String]  = stringKey("stage_name")
  val isTerminalStageKey: AttributeKey[String]     = stringKey("is_terminal")
}
