package io.scalac.extension.actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.util.AggMetric.LongValueAggMetric

final case class ActorMetrics(
  mailboxSize: Option[Int],
  mailboxTime: Option[LongValueAggMetric],
  processedMessages: Option[Long],
  timestamp: Timestamp = Timestamp.create()
)
