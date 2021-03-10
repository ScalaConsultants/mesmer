package io.scalac.extension.metric

import io.opentelemetry.api.common.{ Labels => OpenTelemetryLabels }
import io.scalac.core.model.Tag._
import io.scalac.extension.model._

object StreamOperatorMetricsMonitor {

  case class Labels(
    operator: StageName,
    stream: StreamName,
    terminal: Boolean,
    node: Option[Node],
    connectedWith: Option[(String, Direction)]
  ) {
    def toOpenTelemetry: OpenTelemetryLabels = {
      val terminalLabels = if (terminal) Seq("terminal", "true") else Seq.empty

      val required: Seq[String] = (operator.serialize ++ stream.serialize).flatMap { case (name, value) =>
        Seq(name, value)
      } ++ terminalLabels
      val connected = connectedWith.fold[Seq[String]](Seq.empty) { case (name, direction) =>
        val (directionHeader, directionValue) = direction.serialize
        Seq("connected_with", name, directionHeader, directionValue)
      }

      val optional: Seq[String] =
        node.map(n => Seq("node", n)).getOrElse(Seq.empty) ++ connected
      OpenTelemetryLabels.of(optional ++ required: _*)
    }
  }

  //TODO split processedMessages into processTime, processMessages and demand
  trait BoundMonitor extends Bound {
    def processedMessages: LazyMetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
    def operators: LazyMetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
  }
}
