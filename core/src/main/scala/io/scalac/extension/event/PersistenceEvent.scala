package io.scalac.extension.event

import akka.actor.ActorPath
import akka.actor.typed.receptionist.ServiceKey
import io.scalac.`extension`._

sealed trait AbstractEvent { self =>
  type Service
}

sealed trait Event[T] extends AbstractEvent {
  type Service <: T
}


sealed trait PersistenceEvent extends Event[PersistenceEvent]

object PersistenceEvent {
  case class RecoveryStarted(path: ActorPath, timestamp: Long)  extends PersistenceEvent
  case class RecoveryFinished(path: ActorPath, timestamp: Long) extends PersistenceEvent
}

sealed trait HttpEvent extends Event[HttpEvent] {
  override type Service = HttpEvent
}

object HttpEvent {
  case class RequestStarted(id: String, timestamp: Long, path: String, method: String) extends HttpEvent
  case class RequestCompleted(id: String, timestamp: Long)                             extends HttpEvent
  case class RequestFailed(id: String, timestamp: Long)                                extends HttpEvent
}
