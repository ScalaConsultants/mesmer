package io.scalac.mesmer.extension.http

import io.scalac.mesmer.core.event.HttpEvent.RequestCompleted
import io.scalac.mesmer.core.event.HttpEvent.RequestEvent
import io.scalac.mesmer.core.event.HttpEvent.RequestFailed
import io.scalac.mesmer.core.event.HttpEvent.RequestStarted

trait RequestStorage {
  def requestStarted(event: RequestStarted): RequestStorage
  def requestFailed(event: RequestFailed): Option[(RequestStorage, RequestStarted)]
  def requestCompleted(event: RequestCompleted): Option[(RequestStorage, RequestStarted)]

  protected def eventToKey(event: RequestEvent): String = event.id
}
