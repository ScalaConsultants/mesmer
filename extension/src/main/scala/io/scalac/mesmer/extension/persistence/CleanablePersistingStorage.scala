package io.scalac.mesmer.extension.persistence

import scala.collection.mutable.{ Map => MMap }

import io.scalac.mesmer.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings
import io.scalac.mesmer.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.mesmer.extension.resource.MutableCleanableStorage

class CleanablePersistingStorage private[persistence] (_persist: MMap[PersistEventKey, PersistingEventStarted])(
  val cleaningConfig: CleaningSettings
) extends MutablePersistStorage(_persist)
    with MutableCleanableStorage[PersistEventKey, PersistingEventStarted] {

  protected def extractTimestamp(value: PersistingEventStarted): Timestamp = value.timestamp
}

object CleanablePersistingStorage {
  def withConfig(cleaningConfig: CleaningSettings): CleanablePersistingStorage =
    new CleanablePersistingStorage(MMap.empty)(
      cleaningConfig
    )
}
