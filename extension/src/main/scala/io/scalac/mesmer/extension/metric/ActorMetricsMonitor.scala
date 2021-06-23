package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaActorModule

object ActorMetricsMonitor {
  final case class Labels(actorPath: ActorPath, node: Option[Node] = None, tags: Set[Tag] = Set.empty)
      extends LabelSerializable {
    val serialize: RawLabels = node.serialize ++ actorPath.serialize ++ tags.flatMap(_.serialize)
  }

  trait BoundMonitor extends Bound with AkkaActorModule.Metrics[MetricObserver[Long, Labels]] {}
}
