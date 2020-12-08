package io.scalac.extension.metric
import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._

object PersistenceMetricMonitor {

  final case class Labels(node: Node, path: Path) {
    def toOpenTelemetry: OpenTelemetryLabels = OpenTelemetryLabels.of("node", node, "path", path)
  }
}

trait PersistenceMetricMonitor extends Bindable[Labels] { self =>

  override type Bound <: BoundMonitor

  trait BoundMonitor extends Synchronized {

    def recoveryTime: Instrument[Long] with MetricRecorder[Long]
  }
}
