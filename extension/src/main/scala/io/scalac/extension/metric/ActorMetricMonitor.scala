package io.scalac.extension.metric

import io.scalac.extension.model.{ Node, Path }

import ActorMetricMonitor.Labels

object ActorMetricMonitor {
  case class Labels(actorPath: Path, node: Option[Node])
  trait BoundMonitor extends Synchronized with Bound {
    def mailboxSize: MetricRecorder[Long] with Instrument[Long]
  }
}
