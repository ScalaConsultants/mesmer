package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaHttpModule._

object HttpMetricsMonitor {

  final case class Labels(node: Option[Node], path: Path, method: Method, status: Status) extends LabelSerializable {
    val serialize: RawLabels = node.serialize ++ path.serialize ++ method.serialize ++ status.serialize
  }

  trait BoundMonitor extends AkkaHttpRequestMetricsDef[Metric[Long]] with Synchronized with Bound {
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: Counter[Long] with Instrument[Long]
  }

}
