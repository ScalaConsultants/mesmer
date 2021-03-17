package io.scalac.extension.actor

import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration._

import io.scalac.core.util.{ ReflectionFieldUtils, Timestamp }
import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.LongNoLockAggregator

object ActorTimesDecorators {

  type FieldType = LongNoLockAggregator

  sealed abstract class TimeDecorator(val fieldName: String) {

    private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

    @inline def initialize(actorCell: Object): Unit =
      setter.invoke(actorCell, new FieldType())

    @inline def addTime(actorCell: Object, time: FiniteDuration): Unit =
      getter.invoke(actorCell).asInstanceOf[FieldType].push(TimeSpent(time))

    @inline def getMetrics(actorCell: Object): Option[LongValueAggMetric] =
      getter.invoke(actorCell).asInstanceOf[LongNoLockAggregator].fetch()

  }

  object MailboxTime    extends TimeDecorator("mailboxTimeAgg")
  object ProcessingTime extends TimeDecorator("processingTimeAgg")

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
