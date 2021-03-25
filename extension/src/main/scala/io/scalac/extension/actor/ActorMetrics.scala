package io.scalac.extension.actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.util.AggMetric.LongValueAggMetric

final case class ActorMetrics(
  mailboxSize: Option[Int],
  mailboxTime: Option[LongValueAggMetric],
  receivedMessages: Option[Long],
  unhandledMessages: Option[Long],
  failedMessages: Option[Long],
  processingTime: Option[LongValueAggMetric],
  sentMessages: Option[Long],
  stashSize: Option[Long],
  timestamp: Timestamp = Timestamp.create()
) {
  lazy val processedMessages: Option[Long] = for {
    r <- receivedMessages
    u <- unhandledMessages
  } yield r - u
}
