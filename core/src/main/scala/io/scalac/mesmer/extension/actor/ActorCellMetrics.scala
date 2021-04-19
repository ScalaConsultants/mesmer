package io.scalac.mesmer.extension.actor

import io.scalac.mesmer.core.util.MetricsToolKit._

final case class ActorCellMetrics(
  mailboxTimeAgg: TimeAggregation = new TimeAggregation(),
  processingTimeAgg: TimeAggregation = new TimeAggregation(),
  processingTimer: Timer = new Timer,
  receivedMessages: Counter = new Counter,
  processedMessages: Counter = new Counter,
  unhandledMessages: Counter = new Counter,
  sentMessages: Counter = new Counter,
  failedMessages: Counter = new Counter,
  exceptionHandledMarker: Marker = new Marker,
  stashSize: UninitializedCounter = new UninitializedCounter
)
