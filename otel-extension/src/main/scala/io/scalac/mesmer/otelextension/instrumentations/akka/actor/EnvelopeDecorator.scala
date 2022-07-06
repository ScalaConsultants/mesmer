package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import akka.dispatch.Envelope
import io.opentelemetry.instrumentation.api.field.VirtualField

import io.scalac.mesmer.core.util.Interval
import io.scalac.mesmer.core.util.Timestamp

object EnvelopeDecorator {

  @inline def getInterval(envelope: Envelope): Interval = {
    val timestamp: Timestamp = VirtualField.find(classOf[Envelope], classOf[Timestamp]).get(envelope)
    timestamp.interval()
  }

  @inline def setCurrentTimestamp(envelope: Envelope): Unit =
    VirtualField.find(classOf[Envelope], classOf[Timestamp]).set(envelope, Timestamp.create())
}