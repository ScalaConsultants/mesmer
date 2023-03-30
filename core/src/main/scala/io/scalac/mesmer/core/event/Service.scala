package io.scalac.mesmer.core.event

import akka.actor.typed.receptionist.ServiceKey

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
}
