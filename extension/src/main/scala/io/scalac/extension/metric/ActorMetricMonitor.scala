package io.scalac.extension.metric

import io.scalac.core.Tag
import io.scalac.extension.model.{Node, Path}

object ActorMetricMonitor {
  case class Labels(actorPath: Path, node: Option[Node], tags: Seq[Tag] = Seq.empty)
  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricObserver[Long]
  }
}
