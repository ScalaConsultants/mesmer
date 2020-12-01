package io.scalac.extension.event

import akka.actor.ActorPath

sealed trait AbstractEvent { self =>
  type Service >: self.type
}

sealed trait PersistenceEvent extends AbstractEvent {
  override type Service = PersistenceEvent
}

object PersistenceEvent {
  case class RecoveryStarted(path: ActorPath, timestamp: Long)  extends PersistenceEvent
  case class RecoveryFinished(path: ActorPath, timestamp: Long) extends PersistenceEvent
}

sealed trait HttpEvent extends AbstractEvent {
  override type Service = HttpEvent
}

object HttpEvent {
  case class RequestStarted(id: String, timestamp: Long, path: String, method: String) extends HttpEvent
  case class RequestCompleted(id: String, timestamp: Long)                             extends HttpEvent
  case class RequestFailed(id: String, timestamp: Long)                                extends HttpEvent
}

case class PathMatcherRegistered()
