package io.scalac.extension.actor

import scala.concurrent.duration.FiniteDuration

import io.scalac.extension.util.TimeSeries

final case class MailboxTime(time: FiniteDuration) extends TimeSeries.Entry[Long] {
  def value: Long = time.toMillis
}
