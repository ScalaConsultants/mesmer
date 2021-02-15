package io.scalac

import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension.event.{ HttpEvent, PersistenceEvent, TagEvent }

package object extension {

  val persistenceServiceKey: ServiceKey[PersistenceEvent] =
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")

  val httpServiceKey: ServiceKey[HttpEvent] =
    ServiceKey[HttpEvent](s"io.scalac.metric.http")

  val tagServiceKey: ServiceKey[TagEvent] = ServiceKey[TagEvent]("io.scalac.meta.tag")

}
