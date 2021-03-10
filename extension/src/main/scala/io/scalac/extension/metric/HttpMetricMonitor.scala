package io.scalac.extension.metric

import io.scalac.core.model._

object HttpMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, method: Method)

  implicit val httpMetricsLabelsSerialize: LabelSerializer[Labels] = labels => {
    import labels._
    node.serialize ++ path.serialize ++ method.serialize
  }

  trait BoundMonitor extends Synchronized with Bound {
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: UpCounter[Long] with Instrument[Long]
  }

}
