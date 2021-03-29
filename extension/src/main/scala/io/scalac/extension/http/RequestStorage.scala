package io.scalac.extension.http

import io.scalac.extension.event.HttpEvent.{ RequestCompleted, RequestEvent, RequestFailed, RequestStarted }

trait RequestStorage {
  def requestStarted(event: RequestStarted): RequestStorage
  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)]
  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)]

  protected def eventToKey(event: RequestEvent): String = event.id
}
