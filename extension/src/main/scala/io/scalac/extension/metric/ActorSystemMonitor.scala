package io.scalac.extension.metric

import io.scalac.core.LabelSerializable
import io.scalac.core.model._

object ActorSystemMonitor {
  case class Labels(node: Option[Node]) extends LabelSerializable {
    override def serialize: RawLabels = node.serialize
  }

  trait BoundMonitor extends Bound {
    def createdActors: Counter[Long]
    def terminatedActors: Counter[Long]
  }
}
