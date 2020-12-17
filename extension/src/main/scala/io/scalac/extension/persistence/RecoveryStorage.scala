package io.scalac.extension.persistence

import io.scalac.extension.event.PersistenceEvent.{ RecoveryEvent, RecoveryFinished, RecoveryStarted }

trait RecoveryStorage {

  def recoveryStarted(event: RecoveryStarted): RecoveryStorage

  def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)]

}

class InMemoryRecoveryStorage private (private val recoveries: Map[String, RecoveryStarted]) extends RecoveryStorage {

  protected def eventToKey(event: RecoveryEvent): String = event match {
    case RecoveryStarted(_, persistenceId, _)  => persistenceId
    case RecoveryFinished(_, persistenceId, _) => persistenceId
  }

  override def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    val key = eventToKey(event)
    new InMemoryRecoveryStorage(recoveries + (key -> event))
  }

  override def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] = {
    val key = eventToKey(event)
    recoveries
      .get(key)
      .map { start =>
        val recoveryDuration = event.timestamp - start.timestamp
        (new InMemoryRecoveryStorage(recoveries - key), recoveryDuration)
      }
  }
}

object InMemoryRecoveryStorage {
  def empty: InMemoryRecoveryStorage = new InMemoryRecoveryStorage(Map.empty)
}
