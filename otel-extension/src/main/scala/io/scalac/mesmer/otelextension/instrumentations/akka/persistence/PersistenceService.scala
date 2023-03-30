package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.mesmer.core.event.Service

object PersistenceService {
  implicit val persistenceService: Service[PersistenceEvent] = Service(
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")
  )

}
