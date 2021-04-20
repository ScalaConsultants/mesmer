package io.scalac.mesmer.extension.http

import scala.collection.mutable

import io.scalac.mesmer.core.event.HttpEvent.RequestCompleted
import io.scalac.mesmer.core.event.HttpEvent.RequestFailed
import io.scalac.mesmer.core.event.HttpEvent.RequestStarted
import io.scalac.mesmer.extension.resource.MutableStorage

class MutableRequestStorage private[http] (protected val buffer: mutable.Map[String, RequestStarted])
    extends MutableStorage[String, RequestStarted]
    with RequestStorage {

  def requestStarted(event: RequestStarted): RequestStorage = {
    buffer.put(eventToKey(event), event)
    this
  }

  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)] =
    buffer.remove(eventToKey(event)).map(started => (this, started))

  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)] =
    buffer.remove(eventToKey(event)).map(started => (this, started))
}

object MutableRequestStorage {
  def empty: MutableRequestStorage = new MutableRequestStorage(mutable.Map.empty)
}
