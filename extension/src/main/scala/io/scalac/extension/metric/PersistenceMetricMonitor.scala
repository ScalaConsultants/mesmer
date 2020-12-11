package io.scalac.extension.metric
import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._

object PersistenceMetricMonitor {
  trait BoundMonitor {
    def recoveryTime: MetricRecorder[Long]
  }
  final case class Labels(node: Node, path: Path, persistenceId: String) {
    def toOpenTelemetry: OpenTelemetryLabels =
      OpenTelemetryLabels.of("node", node, "path", path, "persistenceId", persistenceId)
  }
}

trait PersistenceMetricMonitor extends Bindable[Labels] {
  import PersistenceMetricMonitor._

  override type Bound = BoundMonitor
}
