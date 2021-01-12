package io.scalac.extension.http

import io.scalac.extension.event.HttpEvent.{RequestCompleted, RequestFailed, RequestStarted}
import io.scalac.extension.resource.MutableStorage

import scala.collection.mutable

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
  def empty: MutableRequestStorage = new MutableRequestStorage(mutable.Map.empty)
}