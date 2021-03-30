package io.scalac.extension.persistence

import io.scalac.extension.event.PersistenceEvent.RecoveryFinished
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted

class ImmutableRecoveryStorage private[persistence] (private val recoveries: Map[String, RecoveryStarted])
    extends RecoveryStorage {

  override def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    val key = eventToKey(event)
    new ImmutableRecoveryStorage(recoveries + (key -> event))
  }

  override def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] = {
    val key = eventToKey(event)
    recoveries
      .get(key)
      .map { start =>
        val recoveryDuration = calculate(start, event)
        (new ImmutableRecoveryStorage(recoveries - key), recoveryDuration)
      }
  }
}

object ImmutableRecoveryStorage {
  def empty: ImmutableRecoveryStorage = new ImmutableRecoveryStorage(Map.empty)
}
