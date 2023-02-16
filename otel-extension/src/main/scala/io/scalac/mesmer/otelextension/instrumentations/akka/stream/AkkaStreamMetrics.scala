package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.model.Node

final class AkkaStreamMetrics(node: Option[Node]) {
  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")
  private val attributes   = AkkaStreamAttributes.forNode(node)

  @volatile private var runningStreamsTotal: Option[Long] = None
  @volatile private var runningActorsTotal: Option[Long]  = None

  meter.gaugeBuilder("mesmer_akka_streams_running_streams").ofLongs().buildWithCallback { measurement =>
    runningStreamsTotal.map(v => measurement.record(v, attributes))
  }

  meter.gaugeBuilder("mesmer_akka_streams_actors").ofLongs().buildWithCallback { measurement =>
    runningActorsTotal.map(v => measurement.record(v, attributes))
  }

  def setRunningStreamsTotal(value: Long): Unit = runningStreamsTotal = Some(value)
  def setRunningActorsTotal(value: Long): Unit  = runningActorsTotal = Some(value)
}
