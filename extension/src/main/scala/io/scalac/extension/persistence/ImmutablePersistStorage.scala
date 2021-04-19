package io.scalac.extension.persistence

import io.scalac.core.event.PersistenceEvent.PersistingEventFinished
import io.scalac.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.extension.persistence.PersistStorage.PersistEventKey

class ImmutablePersistStorage private (private val persist: Map[PersistEventKey, PersistingEventStarted])
    extends PersistStorage {

  def persistEventStarted(event: PersistingEventStarted): PersistStorage = {
    val key = eventToKey(event)
    new ImmutablePersistStorage(persist + (key -> event))
  }

  def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)] = {
    val key = eventToKey(event)
    persist.get(key).map { started =>
      val duration = calculate(started, event)
      (new ImmutablePersistStorage(persist - key), duration)
    }
  }
}

object ImmutablePersistStorage {

  def empty: ImmutablePersistStorage = new ImmutablePersistStorage(Map.empty)
}
