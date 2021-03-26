package io.scalac.agent.akka.actor

import akka.dispatch.Envelope

import io.scalac.core.util.{ ReflectionFieldUtils, Timestamp }

object EnvelopeDecorator {

  val TimestampVarName = "timestamp"

  private lazy val (timestampGetterHandler, timestampSetterHandler) =
    ReflectionFieldUtils.getHandlers(classOf[Envelope], TimestampVarName)

  @inline final def setTimestamp(envelope: Object): Unit =
    timestampSetterHandler.invoke(envelope, Timestamp.create())

  @inline final def getTimestamp(envelope: Object): Timestamp =
    timestampGetterHandler.invoke(envelope).asInstanceOf[Timestamp]

}
