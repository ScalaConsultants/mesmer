package io.scalac.mesmer.extension.persistence

import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted

class ImmutableRecoveryStorage private[persistence] (private val recoveries: Map[String, RecoveryStarted])
    extends RecoveryStorage {

  def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    val key = eventToKey(event)
    new ImmutableRecoveryStorage(recoveries + (key -> event))
  }

  def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] = {
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
