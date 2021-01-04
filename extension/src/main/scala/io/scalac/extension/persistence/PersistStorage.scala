package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.config.FlushConfig
import io.scalac.extension.event.PersistenceEvent._
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.resource.SelfCleaning

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

trait PersistStorage {
  def persistEventStarted(event: PersistingEventStarted): PersistStorage
  def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)]

  protected def eventToKey(event: PersistEvent): PersistEventKey = event match {
    case PersistingEventStarted(_, persistenceId, seq, _)  => PersistEventKey(persistenceId, seq)
    case PersistingEventFinished(_, persistenceId, seq, _) => PersistEventKey(persistenceId, seq)
  }

  protected def calculate(start: PersistingEventStarted, finish: PersistingEventFinished): Long =
    finish.timestamp - start.timestamp
}

object PersistStorage {
  private[persistence] case class PersistEventKey(persistenceId: String, sequenceNr: Long)
}

class InMemoryPersistStorage private (private val persist: Map[PersistEventKey, PersistingEventStarted])
    extends PersistStorage {

  override def persistEventStarted(event: PersistingEventStarted): PersistStorage = {
    val key = eventToKey(event)
    new InMemoryPersistStorage(persist + (key -> event))
  }

  override def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)] = {
    val key = eventToKey(event)
    persist.get(key).map { started =>
      val duration = calculate(started, event)
      (new InMemoryPersistStorage(persist - key), duration)
    }
  }
}

object InMemoryPersistStorage {

  def empty: InMemoryPersistStorage = new InMemoryPersistStorage(Map.empty)
}

class MutablePersistStorage private[persistence] (
  private[persistence] val persist: CMap[PersistEventKey, PersistingEventStarted]
) extends PersistStorage {

  override def persistEventStarted(event: PersistingEventStarted): PersistStorage = {
    persist.putIfAbsent(eventToKey(event), event)
    this
  }

  override def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)] =
    persist.remove(eventToKey(event)).map { started =>
      val latency = calculate(started, event)
      (this, latency)
    }
}

object MutablePersistStorage {
  def empty: MutablePersistStorage =
    new MutablePersistStorage(new ConcurrentHashMap[PersistEventKey, PersistingEventStarted]().asScala)
}

class CleaningPersistingStorage private[persistence] (persist: CMap[PersistEventKey, PersistingEventStarted])(
  flushConfig: FlushConfig
) extends MutablePersistStorage(persist)
    with SelfCleaning {

  override def clean(): Unit = {
    val thresh = System.currentTimeMillis() - flushConfig.maxStaleness
    for {
      key <- persist.keysIterator
    } persist.updateWith(key) {
      case Some(v) if v.timestamp < thresh => None
      case v                               => v
    }
  }
}

object CleaningPersistingStorage {
  def withConfig(flushConfig: FlushConfig): CleaningPersistingStorage =
    new CleaningPersistingStorage(new ConcurrentHashMap[PersistEventKey, PersistingEventStarted]().asScala)(flushConfig)
}
