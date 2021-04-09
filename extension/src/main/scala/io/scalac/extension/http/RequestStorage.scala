package io.scalac.extension.http

import io.scalac.core.event.HttpEvent.RequestCompleted
import io.scalac.core.event.HttpEvent.RequestEvent
import io.scalac.core.event.HttpEvent.RequestFailed
import io.scalac.core.event.HttpEvent.RequestStarted

trait RequestStorage {
  def requestStarted(event: RequestStarted): RequestStorage
  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)]
  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)]

  protected def eventToKey(event: RequestEvent): String = event.id
}
