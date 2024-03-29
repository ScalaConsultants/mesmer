package io.scalac.mesmer.otelextension.instrumentations.akka.stream

import akka.actor.typed.receptionist.ServiceKey

import io.scalac.mesmer.otelextension.instrumentations.akka.common.Service

object StreamService {
  implicit val streamService: Service[StreamEvent] = Service(ServiceKey[StreamEvent]("io.scalac.metric.stream"))
}
