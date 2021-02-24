package io.scalac.agent.akka.actor

import scala.concurrent.duration.FiniteDuration

import io.scalac.core.util.Timestamp

final case class MailboxTime(value: FiniteDuration, timestamp: Timestamp = Timestamp.create())
