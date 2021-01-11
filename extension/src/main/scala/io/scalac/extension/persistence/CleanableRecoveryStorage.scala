package io.scalac.extension.persistence

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.mutable

class CleanableRecoveryStorage private[persistence] (_recoveries: mutable.Map[String, RecoveryStarted])(
  override val cleaningConfig: CleaningConfig
) extends MutableRecoveryStorage(_recoveries)
    with MutableCleanableStorage[String, RecoveryStarted] {

  override protected def extractTimestamp(value: RecoveryStarted): Timestamp = value.timestamp
}

object CleanableRecoveryStorage {
  def withConfig(flushConfig: CleaningConfig): CleanableRecoveryStorage =
    new CleanableRecoveryStorage(mutable.Map.empty)(flushConfig)
}
