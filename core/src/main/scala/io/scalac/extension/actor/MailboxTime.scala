package io.scalac.extension.actor

import scala.concurrent.duration.FiniteDuration

import io.scalac.core.util.Timestamp
import io.scalac.extension.util.TimeSeries

final case class MailboxTime(time: FiniteDuration, timestamp: Timestamp = Timestamp.create())
    extends TimeSeries.Entry[Long] {
  def value: Long = time.toMillis
}
