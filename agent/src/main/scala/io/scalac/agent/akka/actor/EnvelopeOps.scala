package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles

import akka.dispatch.Envelope

import io.scalac.core.util.Timestamp

object EnvelopeOps {

  val TimestampVarName = "timestamp"

  private lazy val lookup = MethodHandles.publicLookup()
  private lazy val (timestampGetterHandler, timestampSetterHandler) = {
    val timestampClass = classOf[Timestamp]
    val envelopeClass  = classOf[Envelope]
    (
      lookup.findGetter(envelopeClass, TimestampVarName, timestampClass),
      lookup.findSetter(envelopeClass, TimestampVarName, timestampClass)
    )
  }

  @inline def setTimestamp(envelope: Object): Unit =
    timestampSetterHandler.invoke(envelope, Timestamp.create())

  @inline def getTimestamp(envelope: Object): Timestamp =
    timestampGetterHandler.invoke(envelope).asInstanceOf[Timestamp]

}
