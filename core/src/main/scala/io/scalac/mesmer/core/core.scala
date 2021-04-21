package io.scalac.mesmer

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.mesmer.core.event._

package object core {

  val actorServiceKey: ServiceKey[ActorEvent] =
    ServiceKey[ActorEvent](s"io.scalac.metric.actor")

  val persistenceServiceKey: ServiceKey[PersistenceEvent] =
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")

  val httpServiceKey: ServiceKey[HttpEvent] =
    ServiceKey[HttpEvent](s"io.scalac.metric.http")

  val streamServiceKey: ServiceKey[StreamEvent] = ServiceKey[StreamEvent]("io.scalac.metric.stream")

}
