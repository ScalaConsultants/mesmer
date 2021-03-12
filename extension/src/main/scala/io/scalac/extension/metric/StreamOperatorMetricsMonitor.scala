package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model.Tag.{ stream, _ }
import io.scalac.core.model._

object StreamOperatorMetricsMonitor {

  final case class Labels(
    operator: StageName,
    stream: StreamName,
    node: Option[Node],
    connectedWith: Option[(String, Direction)]
  ) extends LabelSerializable {
    override val serialize: RawLabels = {

      val connected = connectedWith.fold[RawLabels](Seq.empty) { case (name, direction) =>
        Seq("connected_with" -> name, direction.serialize)
      }
      operator.serialize ++ stream.serialize ++ node.serialize ++ connected
    }
  }

  //TODO split processedMessages into processTime, processMessages and demand
  trait BoundMonitor extends Bound {
    def processedMessages: LazyMetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
    def operators: LazyMetricObserver[Long, StreamOperatorMetricsMonitor.Labels]
  }
}
