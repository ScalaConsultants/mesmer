package io.scalac.extension.persistence

import scala.collection.mutable

import io.scalac.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.extension.resource.MutableStorage

class MutableRecoveryStorage private[persistence] (protected val buffer: mutable.Map[String, RecoveryStarted])
    extends RecoveryStorage
    with MutableStorage[String, RecoveryStarted] {

  override def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    buffer.put(eventToKey(event), event)
    this
  }

  override def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] =
    buffer.remove(eventToKey(event)).map { started =>
      val latency = calculate(started, event)
      (this, latency)
    }
}

object MutableRecoveryStorage {
  def empty: MutableRecoveryStorage =
    new MutableRecoveryStorage(mutable.Map.empty)
}
