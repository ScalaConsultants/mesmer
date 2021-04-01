package io.scalac.extension.persistence

import scala.collection.mutable.{ Map => MMap }

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.resource.MutableCleanableStorage

class CleanablePersistingStorage private[persistence] (_persist: MMap[PersistEventKey, PersistingEventStarted])(
  override val cleaningConfig: CleaningSettings
) extends MutablePersistStorage(_persist)
    with MutableCleanableStorage[PersistEventKey, PersistingEventStarted] {

  override protected def extractTimestamp(value: PersistingEventStarted): Timestamp = value.timestamp
}

object CleanablePersistingStorage {
  def withConfig(cleaningConfig: CleaningSettings): CleanablePersistingStorage =
    new CleanablePersistingStorage(MMap.empty)(
      cleaningConfig
    )
}
