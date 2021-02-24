package io.scalac

import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension.event.{ ActorEvent, ActorInterpreterStats, HttpEvent, PersistenceEvent, TagEvent }

package object extension {

  val actorServiceKey: ServiceKey[ActorEvent] =
    ServiceKey[ActorEvent](s"io.scalac.metric.actor")

  val persistenceServiceKey: ServiceKey[PersistenceEvent] =
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")

  val httpServiceKey: ServiceKey[HttpEvent] =
    ServiceKey[HttpEvent](s"io.scalac.metric.http")

  val tagServiceKey: ServiceKey[TagEvent] = ServiceKey[TagEvent]("io.scalac.meta.tag")

  val streamServiceKey: ServiceKey[ActorInterpreterStats] = ServiceKey[ActorInterpreterStats]("io.scalac.metric.stream")

}
