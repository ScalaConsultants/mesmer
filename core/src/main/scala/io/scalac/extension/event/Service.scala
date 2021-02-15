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

  override def equals(obj: Any): Boolean = obj match {
    case other: Service[T] => other.eq(this) || other.serviceKey.equals(this.serviceKey)
    case _                 => false
  }
}

object Service {

  def apply[T](key: ServiceKey[T]): Service[T] = new Service[T] {
    override val serviceKey: ServiceKey[T] = key
  }

  implicit val persistenceService: Service[PersistenceEvent] = Service(persistenceServiceKey)

  implicit val httpService: Service[HttpEvent] = Service(httpServiceKey)

  implicit val tagService: Service[TagEvent] = Service(tagServiceKey)

}
