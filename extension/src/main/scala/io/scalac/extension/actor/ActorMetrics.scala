package io.scalac.extension.actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.util.TimeSeries.LongTimeSeries

final case class ActorMetrics(
  mailboxSize: Option[Int],
  mailboxTimeSeries: Option[LongTimeSeries],
  timestamp: Timestamp
)
