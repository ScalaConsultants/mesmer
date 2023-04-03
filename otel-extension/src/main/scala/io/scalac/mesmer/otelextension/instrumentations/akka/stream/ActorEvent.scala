package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import io.scalac.mesmer.core.model.ActorRefTags
import io.scalac.mesmer.otelextension.instrumentations.akka.common.AbstractEvent

sealed trait ActorEvent extends Any with AbstractEvent {
  type Service = ActorEvent
}

object ActorEvent {
  final case class TagsSet(details: ActorRefTags) extends AnyVal with ActorEvent
}
