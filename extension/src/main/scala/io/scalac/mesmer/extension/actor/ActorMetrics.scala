package io.scalac.mesmer.extension.actor

import io.scalac.mesmer.core.util.MinMaxSumCountAggregation.LongMinMaxSumCountAggregationImpl

final case class ActorMetrics(
  mailboxSize: Option[Long],
  mailboxTime: Option[LongMinMaxSumCountAggregationImpl],
  receivedMessages: Option[Long],
  unhandledMessages: Option[Long],
  failedMessages: Option[Long],
  processingTime: Option[LongMinMaxSumCountAggregationImpl],
  sentMessages: Option[Long],
  stashSize: Option[Long],
  droppedMessages: Option[Long]
) {
  lazy val processedMessages: Option[Long] =
    for {
      received  <- receivedMessages
      unhandled <- unhandledMessages
    } yield received - unhandled

  /**
   * Adds this ActorMetrics monotonically increasing counter to other.
   * This will leave other metrics untouched - aggregations, gauges etc
   * @param other
   * @return
   */
  def addTo(other: ActorMetrics): ActorMetrics = ActorMetrics(
    mailboxSize = combineLong(mailboxSize, other.mailboxSize),
    mailboxTime = addToAggregation(mailboxTime, other.mailboxTime),
    receivedMessages = combineLong(receivedMessages, other.receivedMessages),
    unhandledMessages = combineLong(unhandledMessages, other.unhandledMessages),
    failedMessages = combineLong(failedMessages, other.failedMessages),
    processingTime = addToAggregation(processingTime, other.processingTime),
    sentMessages = combineLong(sentMessages, other.sentMessages),
    stashSize = combineLong(stashSize, other.stashSize),
    droppedMessages = combineLong(droppedMessages, other.droppedMessages)
  )

  /**
   * Sums this ActorMetrics with other - this means than all counters - monotonically and nonmonotinically increasing
   * are add up. Max and min are computed normally.
   * @param other ActorMetrics to be summed up with this one
   * @return
   */
  def sum(other: ActorMetrics): ActorMetrics = ActorMetrics(
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
    first: Option[LongMinMaxSumCountAggregationImpl],
    second: Option[LongMinMaxSumCountAggregationImpl]
  ): Option[LongMinMaxSumCountAggregationImpl] = combineOption(first, second)(_.sum(_))

  private def addToAggregation(
    first: Option[LongMinMaxSumCountAggregationImpl],
    second: Option[LongMinMaxSumCountAggregationImpl]
  ): Option[LongMinMaxSumCountAggregationImpl] = combineOption(first, second)(_.addTo(_))

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
