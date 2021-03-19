package io.scalac.extension.actor

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration._

import io.scalac.core.util.{ ReflectionFieldUtils, Timestamp }

object ActorTimesDecorators {

  object ProcessingTimeSupport {

    val fieldName = "messageReceiveStart"

    private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

    @inline def initialize(actorCell: Object): Unit =
      setter.invoke(actorCell, new AtomicReference(Timestamp.create()))

    @inline def set(actorCell: Object): Unit =
      getRef(actorCell).set(Timestamp.create())

    @inline def interval(actorCell: Object): FiniteDuration =
      getRef(actorCell).get().interval().milliseconds

    @inline private def getRef(actorCell: Object): AtomicReference[Timestamp] =
      getter.invoke(actorCell).asInstanceOf[AtomicReference[Timestamp]]

  }

}
