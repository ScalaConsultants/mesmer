package io.scalac.extension.actor

import io.scalac.core.util.Timestamp

final case class ActorMetrics(mailboxSize: Option[Int], timestamp: Timestamp)
