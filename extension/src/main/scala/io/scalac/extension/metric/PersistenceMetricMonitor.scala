package io.scalac.extension.metric
import io.opentelemetry.api.common.{ Labels => OTLabels }
import io.scalac.extension.metric.PersistenceMetricMonitor.Labels
import io.scalac.extension.model._

object PersistenceMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, persistenceId: PersistenceId) extends OpenTelemetryLabels {
    def toOpenTelemetry: OTLabels = {
      val required = Seq("path" -> path, "persistenceId" -> persistenceId)
      convert(required, "node" -> node)
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
