package io.scalac.extension.metric

import io.opentelemetry.api.common
import io.scalac.`extension`.model.Node
import io.scalac.extension.metric.ActorSystemMonitor.Labels

object ActorSystemMonitor {
  final case class Labels(systemName: String, node: Option[Node]) extends OpenTelemetryLabels {
    override def toOpenTelemetry: common.Labels = convert(Seq("system_name" -> systemName), "node" -> node)
  }
}

trait ActorSystemMonitor extends Bindable[Labels] {
  override type Bound <: BoundMonitor

  override def bind(labels: Labels): Bound

  trait BoundMonitor {
    def actors: MetricRecorder[Long]
    def actorTreeScanDuration: MetricRecorder[Long]
  }
}
