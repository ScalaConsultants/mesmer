package io.scalac.extension.http

import io.scalac.extension.event.HttpEvent.RequestCompleted
import io.scalac.extension.event.HttpEvent.RequestEvent
import io.scalac.extension.event.HttpEvent.RequestFailed
import io.scalac.extension.event.HttpEvent.RequestStarted

trait RequestStorage {
  def requestStarted(event: RequestStarted): RequestStorage
  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)]
  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)]

  protected def eventToKey(event: RequestEvent): String = event.id
}
