package io.scalac.extension.actor

import io.scalac.core.akka.model.MailboxTime
import io.scalac.core.util.Timestamp

final case class ActorMetrics(
  mailboxSize: Option[Int],
  mailboxTime: Option[MailboxTime],
  timestamp: Timestamp
)
