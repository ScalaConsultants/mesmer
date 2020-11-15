package io.scalac.extension.event

sealed trait PersistenceEvent

object PersistenceEvent {
  case class RecoveryFinished(recoveryTime: Long) extends PersistenceEvent
}
