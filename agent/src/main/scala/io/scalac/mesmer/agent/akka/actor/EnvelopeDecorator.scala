package io.scalac.mesmer.agent.akka.actor

import akka.dispatch.Envelope
import io.opentelemetry.instrumentation.api.field.VirtualField

import io.scalac.mesmer.core.util.Interval
import io.scalac.mesmer.core.util.Timestamp

object EnvelopeDecorator {

  @inline def getInterval(envelope: Envelope): Interval =
    VirtualField.find(classOf[Envelope], classOf[Timestamp]).get(envelope).interval()

  @inline def setCurrentTimestamp(envelope: Envelope): Unit =
    VirtualField.find(classOf[Envelope], classOf[Timestamp]).set(envelope, Timestamp.create())
}
