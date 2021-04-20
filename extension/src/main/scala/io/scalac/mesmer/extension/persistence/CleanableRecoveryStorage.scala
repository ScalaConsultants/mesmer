package io.scalac.mesmer.extension.persistence

import scala.collection.mutable

import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings
import io.scalac.mesmer.extension.resource.MutableCleanableStorage

class CleanableRecoveryStorage private[persistence] (_recoveries: mutable.Map[String, RecoveryStarted])(
  val cleaningConfig: CleaningSettings
) extends MutableRecoveryStorage(_recoveries)
    with MutableCleanableStorage[String, RecoveryStarted] {

  protected def extractTimestamp(value: RecoveryStarted): Timestamp = value.timestamp
}

object CleanableRecoveryStorage {
  def withConfig(flushConfig: CleaningSettings): CleanableRecoveryStorage =
    new CleanableRecoveryStorage(mutable.Map.empty)(flushConfig)
}
