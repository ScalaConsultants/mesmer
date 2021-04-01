package io.scalac.core.actor

import io.scalac.core.util.AggMetric.LongValueAggMetric
import io.scalac.core.util.Timestamp

final case class ActorMetrics(
  mailboxSize: Option[Long],
  mailboxTime: Option[LongValueAggMetric],
  receivedMessages: Option[Long],
  unhandledMessages: Option[Long],
  failedMessages: Option[Long],
  processingTime: Option[LongValueAggMetric],
  sentMessages: Option[Long],
  stashSize: Option[Long],
  timestamp: Timestamp = Timestamp.create()
) {
  lazy val processedMessages: Option[Long] =
    for {
      received  <- receivedMessages
      unhandled <- unhandledMessages
    } yield received - unhandled
}
