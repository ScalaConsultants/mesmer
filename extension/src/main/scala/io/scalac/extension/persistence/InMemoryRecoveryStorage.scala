package io.scalac.extension.persistence

import io.scalac.extension.event.PersistenceEvent.{ RecoveryFinished, RecoveryStarted }

class InMemoryRecoveryStorage private[persistence] (private val recoveries: Map[String, RecoveryStarted])
    extends RecoveryStorage {

  override def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    val key = eventToKey(event)
    new InMemoryRecoveryStorage(recoveries + (key -> event))
  }

  override def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] = {
    val key = eventToKey(event)
    recoveries
      .get(key)
      .map { start =>
        val recoveryDuration = calculate(start, event)
        (new InMemoryRecoveryStorage(recoveries - key), recoveryDuration)
      }
  }
}

object InMemoryRecoveryStorage {
  def empty: InMemoryRecoveryStorage = new InMemoryRecoveryStorage(Map.empty)
}
