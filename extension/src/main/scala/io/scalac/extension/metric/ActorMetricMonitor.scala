package io.scalac.extension.metric

import io.scalac.core.model.Tag
import io.scalac.extension.model.{Node, Path}

object ActorMetricMonitor {
  case class Labels(actorPath: Path, node: Option[Node], tags: Set[Tag] = Set.empty)
  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricObserver[Long]
    def stashSize: MetricRecorder[Long] with Instrument[Long]
  }
}
