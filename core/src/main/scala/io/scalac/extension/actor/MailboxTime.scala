package io.scalac.extension.actor

import scala.concurrent.duration.FiniteDuration

import io.scalac.core.model.Entry
import io.scalac.core.util.Timestamp

final case class MailboxTime(time: FiniteDuration, timestamp: Timestamp = Timestamp.create()) extends Entry[Long] {
  def value: Long = time.toMillis
}
