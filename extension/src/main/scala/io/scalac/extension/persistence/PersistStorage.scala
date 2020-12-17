package io.scalac.extension.persistence

import io.scalac.extension.event.PersistenceEvent.{ PersistEvent, PersistingEventFinished, PersistingEventStarted }
import io.scalac.extension.persistence.InMemoryPersistStorage.PersistEventKey

trait PersistStorage {
  def persistEventStarted(event: PersistingEventStarted): PersistStorage
  def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)]
}

class InMemoryPersistStorage private (private val persist: Map[PersistEventKey, PersistingEventStarted])
    extends PersistStorage {
  protected def eventToKey(event: PersistEvent): PersistEventKey = event match {
    case PersistingEventStarted(_, persistenceId, seq, _)  => PersistEventKey(persistenceId, seq)
    case PersistingEventFinished(_, persistenceId, seq, _) => PersistEventKey(persistenceId, seq)
  }

  override def persistEventStarted(event: PersistingEventStarted): PersistStorage = {
    val key = eventToKey(event)
    new InMemoryPersistStorage(persist + (key -> event))
  }

  override def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)] = {
    val key = eventToKey(event)
    persist.get(key).map { started =>
      val duration = event.timestamp - started.timestamp
      (new InMemoryPersistStorage(persist - key), duration)
    }
  }
}

object InMemoryPersistStorage {

  protected case class PersistEventKey(persistenceId: String, sequenceNr: Long)
  def empty: InMemoryPersistStorage = new InMemoryPersistStorage(Map.empty)
}
