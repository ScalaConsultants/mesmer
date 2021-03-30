package io.scalac.extension.http

import scala.collection.mutable

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.extension.event.HttpEvent.RequestStarted
import io.scalac.extension.resource.MutableCleanableStorage

class CleanableRequestStorage private[http] (_buffer: mutable.Map[String, RequestStarted])(
  override val cleaningConfig: CleaningSettings
) extends MutableRequestStorage(_buffer)
    with MutableCleanableStorage[String, RequestStarted] {
  override protected def extractTimestamp(value: RequestStarted): Timestamp = value.timestamp
}

object CleanableRequestStorage {
  def withConfig(cleaningConfig: CleaningSettings): CleanableRequestStorage =
    new CleanableRequestStorage(mutable.Map.empty)(cleaningConfig)
}
