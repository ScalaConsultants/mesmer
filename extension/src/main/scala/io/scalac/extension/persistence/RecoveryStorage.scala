package io.scalac.extension.persistence

import io.scalac.core.event.PersistenceEvent.RecoveryEvent
import io.scalac.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.core.event.PersistenceEvent.RecoveryStarted

trait RecoveryStorage {

  def recoveryStarted(event: RecoveryStarted): RecoveryStorage

  def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)]

  protected def eventToKey(event: RecoveryEvent): String = event match {
    case RecoveryStarted(_, persistenceId, _)  => persistenceId
    case RecoveryFinished(_, persistenceId, _) => persistenceId
  }

  protected def calculate(start: RecoveryStarted, finish: RecoveryFinished): Long =
    start.timestamp.interval(finish.timestamp)
}
