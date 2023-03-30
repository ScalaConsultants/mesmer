package io.scalac.mesmer.otelextension.instrumentations.akka.persistence

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.mesmer.otelextension.instrumentations.akka.common.Service

object PersistenceService {
  implicit val persistenceService: Service[PersistenceEvent] = Service(
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")
  )

}
