package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles

import akka.dispatch.Envelope

import io.scalac.core.util.Timestamp

object EnvelopeOps {

  val TimestampVarName = "timestamp"

  private lazy val lookup = MethodHandles.lookup()
  private lazy val (timestampGetterHandler, timestampSetterHandler) = {
    val field = classOf[Envelope].getDeclaredField(TimestampVarName)
    field.setAccessible(true)
    (
      lookup.unreflectGetter(field),
      lookup.unreflectSetter(field)
    )
  }

  @inline def setTimestamp(envelope: Object): Unit =
    timestampSetterHandler.invoke(envelope, Timestamp.create())

  @inline def getTimestamp(envelope: Object): Timestamp =
    timestampGetterHandler.invoke(envelope).asInstanceOf[Timestamp]

}
