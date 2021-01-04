package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.config.FlushConfig
import io.scalac.extension.event.PersistenceEvent.{RecoveryEvent, RecoveryFinished, RecoveryStarted}
import io.scalac.extension.resource.SelfCleaning

import scala.collection.concurrent.{Map => CMap}
import scala.jdk.CollectionConverters._

trait RecoveryStorage {

  def recoveryStarted(event: RecoveryStarted): RecoveryStorage

  def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)]

  protected def eventToKey(event: RecoveryEvent): String = event match {
    case RecoveryStarted(_, persistenceId, _)  => persistenceId
    case RecoveryFinished(_, persistenceId, _) => persistenceId
  }

  protected def calculate(start: RecoveryStarted, finish: RecoveryFinished): Long = finish.timestamp - start.timestamp
}

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
        val recoveryDuration = event.timestamp - start.timestamp
        (new InMemoryRecoveryStorage(recoveries - key), recoveryDuration)
      }
  }
}

object InMemoryRecoveryStorage {
  def empty: InMemoryRecoveryStorage = new InMemoryRecoveryStorage(Map.empty)
}

class MutableRecoveryStorage private[persistence] (private[persistence] val recoveries: CMap[String, RecoveryStarted])
    extends RecoveryStorage {

  override def recoveryStarted(event: RecoveryStarted): RecoveryStorage = {
    recoveries.putIfAbsent(eventToKey(event), event)
    this
  }

  override def recoveryFinished(event: RecoveryFinished): Option[(RecoveryStorage, Long)] =
    recoveries.remove(eventToKey(event)).map { started =>
      val latency = calculate(started, event)
      (this, latency)
    }
}

object MutableRecoveryStorage {
  def empty: MutableRecoveryStorage =
    new MutableRecoveryStorage(new ConcurrentHashMap[String, RecoveryStarted]().asScala)
}



class FlushingRecoveryStorage private[persistence] (recoveries: CMap[String, RecoveryStarted])(
  flushConfig: FlushConfig
) extends MutableRecoveryStorage(recoveries)
    with SelfCleaning {

  override def clean(): Unit = {
    val thresh = System.currentTimeMillis() - flushConfig.maxStaleness
    for {
      key <- recoveries.keysIterator
    } recoveries.updateWith(key) {
      case Some(v) if v.timestamp < thresh => None
      case v                               => v
    }
  }
}

object FlushingRecoveryStorage {
  def withConfig(flushConfig: FlushConfig): FlushingRecoveryStorage =
    new FlushingRecoveryStorage(new ConcurrentHashMap[String, RecoveryStarted]().asScala)(flushConfig)
}
