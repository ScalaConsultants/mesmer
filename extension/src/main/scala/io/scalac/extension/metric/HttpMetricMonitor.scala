package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object HttpMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, method: Method, status: Status) extends LabelSerializable {
    val serialize: RawLabels = node.serialize ++ path.serialize ++ method.serialize ++ status.serialize
  }

  trait BoundMonitor extends Synchronized with Bound {
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: Counter[Long] with Instrument[Long]
  }

}
