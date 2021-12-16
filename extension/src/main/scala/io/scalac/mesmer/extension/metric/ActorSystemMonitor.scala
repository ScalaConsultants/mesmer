package io.scalac.mesmer.extension.metric

import io.scalac.mesmer.core.AttributesSerializable
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.module.AkkaActorSystemModule

object ActorSystemMonitor {
  final case class Attributes(node: Option[Node]) extends AttributesSerializable {
    override def serialize: RawAttributes = node.serialize
  }

  trait BoundMonitor extends Bound with AkkaActorSystemModule.All[Metric[Long]] {
    def createdActors: Counter[Long]
    def terminatedActors: Counter[Long]
  }
}
