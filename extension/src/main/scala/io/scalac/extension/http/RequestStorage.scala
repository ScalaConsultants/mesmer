package io.scalac.extension.http

import java.util.concurrent.ConcurrentHashMap

import io.scalac.extension.config.CleaningConfig
import io.scalac.extension.event.HttpEvent
import io.scalac.extension.event.HttpEvent.{ RequestCompleted, RequestFailed, RequestStarted }
import io.scalac.extension.resource.{ MutableCleanableStorage, MutableStorage }

import scala.collection.mutable
import scala.jdk.CollectionConverters._

trait RequestStorage {
  def requestStarted(event: RequestStarted): RequestStorage
  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)]
  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)]

  protected def eventToKey(event: HttpEvent): String = event match {
    case RequestStarted(id, _, _, _) => id
    case RequestCompleted(id, _)     => id
    case RequestFailed(id, _)        => id
  }
}

class MutableRequestStorage private[http] (protected val buffer: mutable.Map[String, RequestStarted])
    extends MutableStorage[String, RequestStarted]
    with RequestStorage {

  override def requestStarted(event: RequestStarted): RequestStorage = {
    buffer.put(eventToKey(event), event)
    this
  }

  override def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)] =
    buffer.remove(eventToKey(event)).map(started => (this, started))

  override def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)] =
    buffer.remove(eventToKey(event)).map(started => (this, started))
}

object MutableRequestStorage {
  def empty: MutableRequestStorage = new MutableRequestStorage(new ConcurrentHashMap[String, RequestStarted]().asScala)
}

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
