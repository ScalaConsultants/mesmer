package io.scalac.extension.actor

import scala.concurrent.duration.FiniteDuration

import io.scalac.core.util.Timestamp

final case class MailboxTime(time: FiniteDuration, timestamp: Timestamp = Timestamp.create())
