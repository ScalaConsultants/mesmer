package io.scalac.extension.event

import akka.actor.ActorPath
import akka.actor.typed.receptionist.ServiceKey
import io.scalac.`extension`._

sealed trait MonitoredEvent {
  self =>
  type Service >: self.type
  def serviceKey: ServiceKey[Service]
}

sealed trait PersistenceEvent extends MonitoredEvent {
  override type Service = PersistenceEvent

  override val serviceKey: ServiceKey[Service] = persistenceService
}

object PersistenceEvent {
  case class RecoveryStarted(path: String, persistenceId: String, timestamp: Long)  extends PersistenceEvent
  case class RecoveryFinished(path: String, persistenceId: String, timestamp: Long) extends PersistenceEvent
}

sealed trait HttpEvent extends MonitoredEvent {
  override type Service = HttpEvent

  override def serviceKey: ServiceKey[HttpEvent] = httpService
}

object HttpEvent {
  case class RequestStarted(id: String, timestamp: Long, path: String, method: String) extends HttpEvent
  case class RequestCompleted(id: String, timestamp: Long)                             extends HttpEvent
  case class RequestFailed(id: String, timestamp: Long)                                extends HttpEvent
}
