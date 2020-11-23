package io.scalac.extension.event

import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension._

trait AbstractService {
  type ServiceType
}

trait Service[T] extends AbstractService {
  override type ServiceType = T
  def serviceKey: ServiceKey[T]

  override def hashCode(): Int = serviceKey.hashCode()

  override def equals(obj: Any): Boolean =
    (obj.isInstanceOf[Service[T]] && obj.asInstanceOf[Service[T]].serviceKey.equals(serviceKey))
}

object Service {
  implicit val persistenceService: Service[PersistenceEvent] = new Service[PersistenceEvent] {
    override val serviceKey: ServiceKey[PersistenceEvent] = persistenceServiceKey
  }

  implicit val httpService: Service[HttpEvent] = new Service[HttpEvent] {
    override val serviceKey: ServiceKey[HttpEvent] = httpServiceKey
  }
}
