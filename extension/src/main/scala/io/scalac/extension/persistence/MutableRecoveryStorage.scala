package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.event.PersistenceEvent.{ RecoveryFinished, RecoveryStarted }
import io.scalac.extension.resource.MutableStorage

import scala.collection.mutable.{ Map => MMap }
import scala.jdk.CollectionConverters._

class MutableRecoveryStorage private[persistence] (protected val buffer: MMap[String, RecoveryStarted])
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
    new MutableRecoveryStorage(new ConcurrentHashMap[String, RecoveryStarted]().asScala)
}
