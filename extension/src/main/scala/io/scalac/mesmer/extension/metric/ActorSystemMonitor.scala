package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.LabelSerializable
import io.scalac.mesmer.core.model._

object ActorSystemMonitor {
  final case class Labels(node: Option[Node]) extends LabelSerializable {
    override def serialize: RawLabels = node.serialize
  }

  trait BoundMonitor extends Bound {
    def createdActors: Counter[Long]
    def terminatedActors: Counter[Long]
  }
}
