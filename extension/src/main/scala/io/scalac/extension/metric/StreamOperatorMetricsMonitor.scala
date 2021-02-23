package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.extension.model._

object StreamOperatorMetricsMonitor {

  case class Labels(
    node: Option[Node],
    stream: Option[String],
    operator: String,
    direction: Direction,
    connectionWith: String
  ) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val required: Seq[String] = Seq("operator", operator, "connectionWith", connectionWith) ++ direction.serialize
      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty) ++ stream.map(n => Seq("stream", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional ++ required: _*)
    }
  }

  trait BoundMonitor extends Bound {
    def processedMessages: UpCounter[Long]
    def connections: Counter[Long]
  }

}

//object
