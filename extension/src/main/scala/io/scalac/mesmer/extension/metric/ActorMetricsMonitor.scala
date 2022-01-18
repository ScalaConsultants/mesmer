package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaActorModule

object ActorMetricsMonitor {
  final case class Attributes(actorPath: ActorPath, node: Option[Node] = None, tags: Set[Tag] = Set.empty)
      extends AttributesSerializable {
    val serialize: RawAttributes = node.serialize ++ actorPath.serialize ++ tags.flatMap(_.serialize)
  }

  trait BoundMonitor extends Bound with AkkaActorModule.Metrics[MetricObserver[Long, Attributes]] {}
}
