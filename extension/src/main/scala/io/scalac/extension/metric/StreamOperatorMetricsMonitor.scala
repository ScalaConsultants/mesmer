package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model.Tag._
import io.scalac.core.model._

object StreamOperatorMetricsMonitor {

  final case class Labels(
    operator: StageName,
    stream: StreamName,
    terminal: Boolean,
    node: Option[Node],
    connectedWith: Option[String] // TODO change this to StageName
  ) extends LabelSerializable {
    val serialize: RawLabels = {

      val connected = connectedWith.fold[RawLabels](Seq.empty) { stageName =>
        Seq("connected_with" -> stageName)
      }

      val terminalLabels = if (terminal) Seq("terminal" -> "true") else Seq.empty

      operator.serialize ++ stream.serialize ++ node.serialize ++ connected ++ terminalLabels
    }
  }

  trait BoundMonitor extends Bound {
    def processedMessages: MetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
    def operators: MetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
    def demand: MetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
  }
}
