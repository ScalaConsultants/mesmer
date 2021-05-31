package io.scalac.mesmer.extension.resource

import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings

trait MutableCleanableStorage[K, V] extends SelfCleaning with MutableStorage[K, V] {
  protected def cleaningConfig: CleaningSettings
  protected def extractTimestamp(value: V): Timestamp
  protected def currentTimestamp: Timestamp = Timestamp.create()

  def clean(): Unit = {
    val current        = currentTimestamp
    val maxStalenessMs = cleaningConfig.maxStaleness.toMillis
    for {
      key <- buffer.keysIterator
    } buffer.updateWith(key) {
      case Some(v) if extractTimestamp(v).interval(current).toMillis > maxStalenessMs => None
      case v                                                                 => v
    }
  }
}
