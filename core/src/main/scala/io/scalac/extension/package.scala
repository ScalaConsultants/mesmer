package io.scalac

import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension.event.PersistenceEvent

package object `extension` {

  val persistenceService: ServiceKey[PersistenceEvent] =
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")
}
