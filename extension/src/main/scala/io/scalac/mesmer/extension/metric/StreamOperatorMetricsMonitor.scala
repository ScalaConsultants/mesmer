package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model.Tag._
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaStreamModule

object StreamOperatorMetricsMonitor {

  final case class Attributes(
    operator: StageName,
    stream: StreamName,
    terminal: Boolean,
    node: Option[Node],
    connectedWith: Option[String] // TODO change this to StageName
  ) extends AttributesSerializable {
    val serialize: RawAttributes = {

      val connected = connectedWith.fold[RawAttributes](Seq.empty) { stageName =>
        Seq("connected_with" -> stageName)
      }

      val terminalAttributes = if (terminal) Seq("terminal" -> "true") else Seq.empty

      operator.serialize ++ stream.serialize ++ node.serialize ++ connected ++ terminalAttributes
    }
  }

  trait BoundMonitor extends Bound with AkkaStreamModule.StreamOperatorMetricsDef[Metric[Long]] {
    def processedMessages: MetricObserver[Long, StreamOperatorMetricsMonitor.Attributes]
    def operators: MetricObserver[Long, StreamOperatorMetricsMonitor.Attributes]
    def demand: MetricObserver[Long, StreamOperatorMetricsMonitor.Attributes]
  }
}
