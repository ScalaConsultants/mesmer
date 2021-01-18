package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OTLabels }
import io.scalac.extension.metric.HttpMetricMonitor._
import io.scalac.extension.model._

object HttpMetricMonitor {

  final case class Labels(node: Option[Node], path: Path, method: Method) extends OpenTelemetryLabels {

    def toOpenTelemetry: OTLabels = {
      val required = Seq("path" -> path, "method" -> method)
      convert(required, ("node" -> node))
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
