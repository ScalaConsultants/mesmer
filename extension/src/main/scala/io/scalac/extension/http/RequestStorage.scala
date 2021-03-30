package io.scalac.extension.http

import io.scalac.core.event.HttpEvent
import io.scalac.core.event.HttpEvent.{ RequestCompleted, RequestFailed, RequestStarted }

trait RequestStorage {
  def requestStarted(event: RequestStarted): RequestStorage
  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)]
  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)]

  protected def eventToKey(event: HttpEvent): String = event.id
}
