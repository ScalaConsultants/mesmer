package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.typed.ActorSystem
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter

import io.scalac.mesmer.core.cluster.ClusterNode.ActorSystemOps

final class AkkaStreamMetrics(actorSystem: ActorSystem[_]) {
  private val meter: Meter    = GlobalOpenTelemetry.getMeter("mesmer")
  private lazy val attributes = AkkaStreamAttributes.forNode(actorSystem.clusterNodeName)

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
