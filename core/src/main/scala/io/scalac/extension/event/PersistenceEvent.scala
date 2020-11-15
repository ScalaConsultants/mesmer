package io.scalac.extension.event

import akka.actor.ActorPath
import akka.actor.typed.receptionist.ServiceKey
import io.scalac.`extension`.persistenceService

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
  case class RecoveryStarted(path: ActorPath, timestamp: Long) extends PersistenceEvent
  case class RecoveryFinished(path: ActorPath, timestamp: Long) extends PersistenceEvent
}
