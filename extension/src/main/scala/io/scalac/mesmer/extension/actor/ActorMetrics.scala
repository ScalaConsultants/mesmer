package io.scalac.mesmer.extension.actor

import io.scalac.mesmer.core.util.AggMetric.LongValueAggMetric
import io.scalac.mesmer.core.util.Timestamp

final case class ActorMetrics(
  mailboxSize: Option[Long],
  mailboxTime: Option[LongValueAggMetric],
  receivedMessages: Option[Long],
  unhandledMessages: Option[Long],
  failedMessages: Option[Long],
  processingTime: Option[LongValueAggMetric],
  sentMessages: Option[Long],
  stashSize: Option[Long],
  droppedMessages: Option[Long]
//  timestamp: Timestamp = Timestamp.create()
) {
  lazy val processedMessages: Option[Long] =
    for {
      received  <- receivedMessages
      unhandled <- unhandledMessages
    } yield received - unhandled

  def combine(other: ActorMetrics): ActorMetrics = ActorMetrics(
    mailboxSize = combineLong(mailboxSize, other.mailboxSize),
    mailboxTime = combineAggregation(mailboxTime, other.mailboxTime),
    receivedMessages = combineLong(receivedMessages, other.receivedMessages),
    unhandledMessages = combineLong(unhandledMessages, other.unhandledMessages),
    failedMessages = combineLong(failedMessages, other.failedMessages),
    processingTime = combineAggregation(processingTime, other.processingTime),
    sentMessages = combineLong(sentMessages, other.sentMessages),
    stashSize = combineLong(stashSize, other.stashSize),
    droppedMessages = combineLong(droppedMessages, other.droppedMessages)
  )

  private def combineLong(first: Option[Long], second: Option[Long]): Option[Long] =
    combineOption(first, second)(_ + _)

  private def combineAggregation(
    first: Option[LongValueAggMetric],
    second: Option[LongValueAggMetric]
  ): Option[LongValueAggMetric] = combineOption(first, second)(_.combine(_))

  private def combineOption[T](first: Option[T], second: Option[T])(combine: (T, T) => T): Option[T] =
    (first, second) match {
      case (None, None)        => None
      case (x @ Some(_), None) => x
      case (None, y @ Some(_)) => y
      case (Some(x), Some(y))  => Some(combine(x, y))
    }
}

object ActorMetrics {
  val empty: ActorMetrics = ActorMetrics(None, None, None, None, None, None, None, None, None)
}
