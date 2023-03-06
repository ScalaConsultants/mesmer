package io.scalac.mesmer.core.model.stream

import io.scalac.mesmer.core.model.Tag.StreamName

final case class StreamStats(
  streamName: StreamName,
  actors: Int,
  stages: Int,
  processesMessages: Long
)
