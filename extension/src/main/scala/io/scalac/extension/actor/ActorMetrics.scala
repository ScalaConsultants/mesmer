package io.scalac.extension.actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.metric.Distribution.LongDistribution

final case class ActorMetrics(
  mailboxSize: Option[Int],
  mailboxTimeDist: Option[LongDistribution],
  timestamp: Timestamp
)
