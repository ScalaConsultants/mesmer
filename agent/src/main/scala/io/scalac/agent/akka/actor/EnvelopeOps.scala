package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles

import io.scalac.core.util.Timestamp

object EnvelopeOps {

  val TimestampVarName = "timestamp"

  private lazy val lookup = MethodHandles.publicLookup()

  private lazy val (timestampGetterHandler, timestampSetterHandler) = {
    val field = Class.forName("akka.dispatch.Envelope").getDeclaredField(TimestampVarName)
    field.setAccessible(true)
    (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
  }

  def setTimestamp(envelope: Object): Unit = timestampSetterHandler.invoke(envelope, Timestamp.create())

  def getTimestamp(envelope: Object): Timestamp = timestampGetterHandler.invoke(envelope).asInstanceOf[Timestamp]

}
