package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter
import io.scalac.mesmer.core.model.Node

import java.util.concurrent.atomic.AtomicReference

final class AkkaStreamMetrics(node: Option[Node]) {
  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")
  private val attributes = AkkaStreamAttributes.forNode(node)

  private val runningStreamsTotal = new AtomicReference[Option[Long]](None)
  private val runningActorsTotal = new AtomicReference[Option[Long]](None)

  meter.gaugeBuilder("mesmer_akka_streams_running_streams").ofLongs().buildWithCallback { measurement =>
    runningStreamsTotal.get.foreach(v => measurement.record(v, attributes))
  }

  meter.gaugeBuilder("mesmer_akka_streams_actors").ofLongs().buildWithCallback { measurement =>
    runningActorsTotal.get.foreach(v => measurement.record(v, attributes))
  }

  def setRunningStreamsTotal(value: Long): Unit = runningStreamsTotal.set(Some(value))
  def setRunningActorsTotal(value: Long): Unit = runningActorsTotal.set(Some(value))

}
