package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.ActorRef

import io.scalac.mesmer.core.model.ShellInfo
import io.scalac.mesmer.core.model.Tag.SubStreamName
import io.scalac.mesmer.otelextension.instrumentations.akka.common.AbstractEvent

sealed trait StreamEvent extends AbstractEvent {
  type Service = StreamEvent
}

object StreamEvent {
  final case class StreamInterpreterStats(ref: ActorRef, streamName: SubStreamName, shellInfo: Set[ShellInfo])
      extends StreamEvent

  /**
   * Indicating that this part of stream has collapsed
   * @param ref
   * @param streamName
   * @param shellInfo
   */
  final case class LastStreamStats(ref: ActorRef, streamName: SubStreamName, shellInfo: ShellInfo) extends StreamEvent
}
