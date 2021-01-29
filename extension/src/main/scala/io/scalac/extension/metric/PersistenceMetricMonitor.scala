package io.scalac.extension.metric
import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._

object PersistenceMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, persistenceId: PersistenceId) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val required = Seq("path", path, "persistenceId", persistenceId)
      val optional = node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional ++ required: _*)

    }
  }
}

trait PersistenceMetricMonitor extends Bindable[Labels] { self =>

  override type Bound <: BoundMonitor

  trait BoundMonitor extends Synchronized {

    def recoveryTime: Instrument[Long] with MetricRecorder[Long]
    def recoveryTotal: Instrument[Long] with UpCounter[Long]
    def persistentEvent: Instrument[Long] with MetricRecorder[Long]
    def persistentEventTotal: Instrument[Long] with UpCounter[Long]
    def snapshot: Instrument[Long] with UpCounter[Long]
  }
}
