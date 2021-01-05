package io.scalac.extension.resource

import io.scalac.extension.config.CleaningConfig

trait MutableCleanableStorage[K, V] extends SelfCleaning with MutableStorage[K, V] {
  protected def cleaningConfig: CleaningConfig
  protected def extractTimestamp(value: V): Long
  protected def timestamp: Long = System.currentTimeMillis()

  override def clean(): Unit = {
    val thresh = timestamp - cleaningConfig.maxStaleness
    for {
      key <- buffer.keysIterator
    } buffer.updateWith(key) {
      case Some(v) if extractTimestamp(v) < thresh => None
      case v                                       => v
    }
  }
}
