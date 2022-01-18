package io.scalac.mesmer.extension.metric
import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaPersistenceModule

object PersistenceMetricsMonitor {

  final case class Attributes(node: Option[Node], path: Path, persistenceId: PersistenceId)
      extends AttributesSerializable {
    val serialize: RawAttributes = node.serialize ++ path.serialize ++ persistenceId.serialize
  }

  trait BoundMonitor extends Bound with AkkaPersistenceModule.Metrics[Metric[Long]] {
    def recoveryTime: Histogram[Long]
    def recoveryTotal: Counter[Long]
    def persistentEvent: Histogram[Long]
    def persistentEventTotal: Counter[Long]
    def snapshot: Counter[Long]
  }

}
