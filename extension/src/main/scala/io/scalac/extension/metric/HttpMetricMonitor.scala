package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.metric.HttpMetricMonitor._
import io.scalac.extension.model._

object HttpMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, method: Method) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val required: Seq[String] = Seq("path", path, "method", method)
      val optional              = node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of((optional ++ required).toArray)
    }
  }
}

trait HttpMetricMonitor extends Bindable[Labels] {
  import HttpMetricMonitor._

  override type Bound <: BoundMonitor

  override def bind(labels: Labels): Bound

  trait BoundMonitor extends Synchronized {
    def requestTime: MetricRecorder[Long] with Instrument[Long]
    def requestCounter: UpCounter[Long] with Instrument[Long]
  }
}
