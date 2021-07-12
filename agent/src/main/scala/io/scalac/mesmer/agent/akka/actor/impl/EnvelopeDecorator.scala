package io.scalac.mesmer.agent.akka.actor.impl

import akka.dispatch.Envelope

import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.core.util.Timestamp

object EnvelopeDecorator {

  val TimestampVarName = "timestamp"

  private lazy val (timestampGetterHandler, timestampSetterHandler) =
    ReflectionFieldUtils.getHandlers(classOf[Envelope], TimestampVarName)

  @inline final def setTimestamp(envelope: Object): Unit =
    timestampSetterHandler.invoke(envelope, Timestamp.create())

  @inline final def getTimestamp(envelope: Object): Timestamp =
    timestampGetterHandler.invoke(envelope).asInstanceOf[Timestamp]

}
