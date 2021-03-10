package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.core.model.Tag.StreamName
import io.scalac.extension.model._

object StreamMetricMonitor {

  case class EagerLabels(
    node: Option[Node]
  ) {
    def toOpenTelemetry: OpenTelemetryLabels = {

      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      OpenTelemetryLabels.of(optional: _*)
    }
  }

  case class Labels(node: Option[Node], streamName: StreamName) {
    def toOpenTelemetry: OpenTelemetryLabels = {

      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty)
      val required: Seq[String] = streamName.serialize.flatMap { case (key, value) =>
        Seq(key, value)
      }
      OpenTelemetryLabels.of((optional ++ required): _*)
    }
  }

  trait BoundMonitor extends Bound {
    def runningStreamsTotal: MetricRecorder[Long]
    def streamActorsTotal: MetricRecorder[Long]
    def streamProcessedMessages: LazyMetricObserver[Long, Labels]
  }

}
