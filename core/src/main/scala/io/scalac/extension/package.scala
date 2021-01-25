package io.scalac

import akka.actor.typed.receptionist.ServiceKey
import io.scalac.extension.event.{ ClusterEvent, HttpEvent, PersistenceEvent }

package object extension {

  val persistenceServiceKey: ServiceKey[PersistenceEvent] =
    ServiceKey[PersistenceEvent](s"io.scalac.metric.persistence")

  val httpServiceKey: ServiceKey[HttpEvent] =
    ServiceKey[HttpEvent](s"io.scalac.metric.http")

  val clusterServiceKey: ServiceKey[ClusterEvent] = ServiceKey[ClusterEvent]("io.scalac.metric.cluster")
}
