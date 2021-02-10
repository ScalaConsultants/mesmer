package io.scalac

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.extension.event.{ ActorEvent, HttpEvent, PersistenceEvent }

package object extension {

  val actorServiceKey: ServiceKey[ActorEvent] =
    ServiceKey[ActorEvent](s"io.scalac.metric.actor")

  val persistenceServiceKey: ServiceKey[PersistenceEvent] =
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")

  val httpServiceKey: ServiceKey[HttpEvent] =
    ServiceKey[HttpEvent](s"io.scalac.metric.http")

}
