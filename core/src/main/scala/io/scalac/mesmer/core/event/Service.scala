package io.scalac.mesmer.core.event

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.mesmer.core._

trait AbstractService {
  type ServiceType
}

trait Service[T] extends AbstractService {
  type ServiceType = T
  def serviceKey: ServiceKey[T]

  override lazy val hashCode: Int = serviceKey.hashCode()

  override def equals(obj: Any): Boolean = obj match {
    case other: Service[T] => other.eq(this) || other.serviceKey.equals(this.serviceKey)
    case _                 => false
  }
}

object Service {

  def apply[T](key: ServiceKey[T]): Service[T] = new Service[T] {
    val serviceKey: ServiceKey[T] = key
  }

  implicit val dispatcherService: Service[DispatcherEvent] = Service(dispatcherServiceKey)

  implicit val actorService: Service[ActorEvent] = Service(actorServiceKey)

  implicit val persistenceService: Service[PersistenceEvent] = Service(persistenceServiceKey)

  implicit val httpService: Service[HttpEvent] = Service(httpServiceKey)

  implicit val streamService: Service[StreamEvent] = Service(streamServiceKey)

}
