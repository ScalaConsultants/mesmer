package io.scalac.mesmer.extension.persistence

import scala.collection.mutable

import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.extension.resource.MutableStorage

class MutableRecoveryStorage private[persistence] (protected val buffer: mutable.Map[String, RecoveryStarted])
    extends RecoveryStorage
    with MutableStorage[String, RecoveryStarted] {

  def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    buffer.put(eventToKey(event), event)
    this
  }

  def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] =
    buffer.remove(eventToKey(event)).map { started =>
      val latency = calculate(started, event)
      (this, latency)
    }
}

object MutableRecoveryStorage {
  def empty: MutableRecoveryStorage =
    new MutableRecoveryStorage(mutable.Map.empty)
}
