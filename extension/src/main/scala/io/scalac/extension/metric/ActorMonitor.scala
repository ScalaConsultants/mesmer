package io.scalac.extension.metric

import io.scalac.extension.model.{ Node, Path }

import ActorMonitor.Labels

object ActorMonitor {
  case class Labels(actorPath: Path, node: Option[Node])
}

trait ActorMonitor extends Bindable[Labels] {
  override type Bound <: BoundMonitor

  trait BoundMonitor extends Synchronized {
    def mailboxSize: MetricRecorder[Long] with Instrument[Long]
  }
}
