package io.scalac.mesmer.extension.http

import scala.collection.mutable

import io.scalac.mesmer.core.event.HttpEvent.RequestStarted
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.extension.config.CleaningSettings
import io.scalac.mesmer.extension.resource.MutableCleanableStorage

class CleanableRequestStorage private[http] (_buffer: mutable.Map[String, RequestStarted])(
  val cleaningConfig: CleaningSettings
) extends MutableRequestStorage(_buffer)
    with MutableCleanableStorage[String, RequestStarted] {
  protected def extractTimestamp(value: RequestStarted): Timestamp = value.timestamp
}

object CleanableRequestStorage {
  def withConfig(cleaningConfig: CleaningSettings): CleanableRequestStorage =
    new CleanableRequestStorage(mutable.Map.empty)(cleaningConfig)
}
