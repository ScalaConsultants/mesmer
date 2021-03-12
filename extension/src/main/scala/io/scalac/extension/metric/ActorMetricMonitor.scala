package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object ActorMetricMonitor {
  final case class Labels(actorPath: ActorPath, node: Option[Node], tags: Set[Tag] = Set.empty)
      extends LabelSerializable {
    override val serialize: RawLabels = node.serialize ++ actorPath.serialize ++ tags.flatMap(_.serialize)
  }

  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricObserver[Long]
    def stashSize: MetricRecorder[Long] with Instrument[Long]
  }
}
