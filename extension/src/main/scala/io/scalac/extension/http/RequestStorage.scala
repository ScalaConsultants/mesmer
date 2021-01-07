package io.scalac.extension.http

import io.scalac.extension.event.HttpEvent
import io.scalac.extension.event.HttpEvent.{RequestCompleted, RequestFailed, RequestStarted}

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








