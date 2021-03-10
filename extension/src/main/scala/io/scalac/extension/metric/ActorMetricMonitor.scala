package io.scalac.extension.metric

import io.scalac.core.model._

object ActorMetricMonitor {
  case class Labels(actorPath: ActorPath, node: Option[Node], tags: Set[Tag] = Set.empty)

  implicit val actorMetriclabelsSerializer: LabelSerializer[Labels] = labels => {
    import labels._
    node.serialize ++ actorPath.serialize ++ tags.flatMap(_.serialize)
  }

  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricObserver[Long]
    def stashSize: MetricRecorder[Long] with Instrument[Long]
  }
}
