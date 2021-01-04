package io.scalac.extension.persistence

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.concurrent.{ Map => CMap }
import scala.jdk.CollectionConverters._

class CleanableRecoveryStorage private[persistence] (_recoveries: CMap[String, RecoveryStarted])(
  override val cleaningConfig: CleaningConfig
) extends MutableRecoveryStorage(_recoveries)
    with MutableCleanableStorage[String, RecoveryStarted] {

  override protected def extractTimestamp(value: RecoveryStarted): Long = value.timestamp
}

object CleanableRecoveryStorage {
  def withConfig(flushConfig: CleaningConfig): CleanableRecoveryStorage =
    new CleanableRecoveryStorage(new ConcurrentHashMap[String, RecoveryStarted]().asScala)(flushConfig)
}
