package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import scala.concurrent.duration.FiniteDuration

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.LongNoLockAggregator

object ActorTimesDecorators {

  type FieldType = LongNoLockAggregator

  sealed abstract class TimeDecorator(val filedName: String) {

    private lazy val (getter, setter) = {
      val field = Class.forName("akka.actor.ActorCell").getDeclaredField(filedName)
      field.setAccessible(true)
      val lookup = MethodHandles.publicLookup()
      (lookup.unreflectGetter(field), lookup.unreflectSetter(field))
    }

    @inline def setAggregator(actorCell: Object): Unit =
      setter.invoke(actorCell, new FieldType())

    @inline def addTime(actorCell: Object, time: FiniteDuration): Unit =
      getter.invoke(actorCell).asInstanceOf[FieldType].push(TimeSpent(time))

    @inline def getMetrics(actorCell: Object): Option[LongValueAggMetric] =
      getter.invoke(actorCell).asInstanceOf[LongNoLockAggregator].fetch()

  }

  object MailboxTime    extends TimeDecorator("mailboxTimeAgg")
  object ProcessingTime extends TimeDecorator("processingTimeAgg")

}
