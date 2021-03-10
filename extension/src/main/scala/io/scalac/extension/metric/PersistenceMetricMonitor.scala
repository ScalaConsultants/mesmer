package io.scalac.extension.metric
import io.scalac.core.model._

object PersistenceMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, persistenceId: PersistenceId)

  implicit val persistenceMetricsLabelsSerializer: LabelSerializer[Labels] = labels => {
    import labels._
    node.serialize ++ path.serialize ++ persistenceId.serialize
  }

  trait BoundMonitor extends Synchronized with Bound {
    def recoveryTime: Instrument[Long] with MetricRecorder[Long]
    def recoveryTotal: Instrument[Long] with UpCounter[Long]
    def persistentEvent: Instrument[Long] with MetricRecorder[Long]
    def persistentEventTotal: Instrument[Long] with UpCounter[Long]
    def snapshot: Instrument[Long] with UpCounter[Long]
  }

}
