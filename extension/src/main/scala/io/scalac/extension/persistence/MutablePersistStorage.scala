package io.scalac.extension.persistence

import scala.collection.mutable

import io.scalac.extension.event.PersistenceEvent.PersistingEventFinished
import io.scalac.extension.event.PersistenceEvent.PersistingEventStarted
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.resource.MutableStorage

class MutablePersistStorage private[persistence] (
  protected val buffer: mutable.Map[PersistEventKey, PersistingEventStarted]
) extends PersistStorage
    with MutableStorage[PersistEventKey, PersistingEventStarted] {

  override def persistEventStarted(event: PersistingEventStarted): PersistStorage = {
    buffer.put(eventToKey(event), event)
    this
  }

  override def persistEventFinished(event: PersistingEventFinished): Option[(PersistStorage, Long)] =
    buffer.remove(eventToKey(event)).map { started =>
      val latency = calculate(started, event)
      (this, latency)
    }
}

object MutablePersistStorage {
  def empty: MutablePersistStorage =
    new MutablePersistStorage(mutable.Map.empty)
}
