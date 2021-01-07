package io.scalac.extension.persistence

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.PersistenceEvent.PersistingEventStarted
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.mutable.{Map => MMap}

class CleanablePersistingStorage private[persistence] (_persist: MMap[PersistEventKey, PersistingEventStarted])(
  override val cleaningConfig: CleaningConfig
) extends MutablePersistStorage(_persist)
    with MutableCleanableStorage[PersistEventKey, PersistingEventStarted] {

  override protected def extractTimestamp(value: PersistingEventStarted): Long = value.timestamp
}

object CleanablePersistingStorage {
  def withConfig(cleaningConfig: CleaningConfig): CleanablePersistingStorage =
    new CleanablePersistingStorage(MMap.empty)(
      cleaningConfig
    )
}
