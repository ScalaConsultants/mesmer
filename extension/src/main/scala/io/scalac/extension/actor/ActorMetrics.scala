package io.scalac.extension.actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.util.AggMetric.LongValueAggMetric

final case class ActorMetrics(
  mailboxSize: Option[Int],
  mailboxTime: Option[LongValueAggMetric],
  receivedMessages: Long,
  unhandledMessages: Long,
  failedMessages: Long,
  processingTime: Option[LongValueAggMetric],
  sentMessages: Long,
  timestamp: Timestamp = Timestamp.create()
) {
  lazy val processedMessages: Long = receivedMessages - unhandledMessages
}
