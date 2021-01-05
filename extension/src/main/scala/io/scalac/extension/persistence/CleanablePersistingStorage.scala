package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.PersistenceEvent.PersistingEventStarted
import io.scalac.extension.persistence.PersistStorage.PersistEventKey
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

class CleanablePersistingStorage private[persistence] (_persist: CMap[PersistEventKey, PersistingEventStarted])(
  override val cleaningConfig: CleaningConfig
) extends MutablePersistStorage(_persist)
    with MutableCleanableStorage[PersistEventKey, PersistingEventStarted] {

  override protected def extractTimestamp(value: PersistingEventStarted): Long = value.timestamp
}

object CleanablePersistingStorage {
  def withConfig(cleaningConfig: CleaningConfig): CleanablePersistingStorage =
    new CleanablePersistingStorage(new ConcurrentHashMap[PersistEventKey, PersistingEventStarted]().asScala)(
      cleaningConfig
    )
}
