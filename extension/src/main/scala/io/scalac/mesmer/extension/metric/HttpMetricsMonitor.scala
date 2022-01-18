package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaHttpModule._

object HttpMetricsMonitor {

  final case class Attributes(node: Option[Node], path: Path, method: Method, status: Status)
      extends AttributesSerializable {
    val serialize: RawAttributes = node.serialize ++ path.serialize ++ method.serialize ++ status.serialize
  }

  trait BoundMonitor extends AkkaHttpRequestMetricsDef[Metric[Long]] with Synchronized with Bound {
    def requestTime: Histogram[Long] with Instrument[Long]
    def requestCounter: Counter[Long] with Instrument[Long]
  }

}
