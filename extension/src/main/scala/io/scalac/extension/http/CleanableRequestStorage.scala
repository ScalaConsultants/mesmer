package io.scalac.extension.http

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.HttpEvent.RequestStarted
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.mutable

class CleanableRequestStorage private[http] (_buffer: mutable.Map[String, RequestStarted])(
  override val cleaningConfig: CleaningConfig
) extends MutableRequestStorage(_buffer)
    with MutableCleanableStorage[String, RequestStarted] {
  override protected def extractTimestamp(value: RequestStarted): Long = value.timestamp
}

object CleanableRequestStorage {
  def withConfig(cleaningConfig: CleaningConfig): CleanableRequestStorage =
    new CleanableRequestStorage(mutable.Map.empty)(cleaningConfig)
}