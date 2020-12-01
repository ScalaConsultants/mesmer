package io.scalac.extension.metric
import io.opentelemetry.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._

object PersistenceMetricMonitor {
  trait BoundMonitor {
    def recoveryTime: MetricRecorder[Long]
  }
  final case class Labels(path: Path, persistenceId: String) {
    def toOpenTelemetry: OpenTelemetryLabels = OpenTelemetryLabels.of("path", path, "persistenceId", persistenceId)
  }
}

trait PersistenceMetricMonitor extends Bindable[Labels] {
  import PersistenceMetricMonitor._

  override type Bound = BoundMonitor
}
