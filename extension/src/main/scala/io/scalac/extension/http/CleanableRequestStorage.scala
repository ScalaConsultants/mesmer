package io.scalac.extension.http

import io.scalac.core.util.Timestamp
import io.scalac.extension.config.CleaningSettings
import io.scalac.core.event.HttpEvent.RequestStarted
import io.scalac.extension.resource.MutableCleanableStorage

import scala.collection.mutable

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
