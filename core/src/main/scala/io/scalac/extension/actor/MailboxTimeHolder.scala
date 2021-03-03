package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.TimeSeries.LongTimeSeries

object MailboxTimeHolder {

  type MailboxTimesType = mutable.ArrayBuffer[MailboxTime]
  type MailboxTimeAgg   = LongValueAggMetric

  val MailboxTimesVar   = "mailboxTimes"
  val MailboxTimeAggVar = "mailboxTimeAgg"

  private val AggSizeToAgg = 100

  private lazy val lookup = MethodHandles.publicLookup()

  private lazy val (
    mailboxTimesGetterHandler,
    mailboxTimesSetterHandler,
    mailboxTimeAggGetterHandler,
    mailboxTimeAggSetterHandler
  ) = {
    val actorCellClass      = Class.forName("akka.actor.ActorCell")
    val mailboxTimesField   = actorCellClass.getDeclaredField(MailboxTimesVar)
    val mailboxTimeAggField = actorCellClass.getDeclaredField(MailboxTimeAggVar)
    mailboxTimesField.setAccessible(true)
    mailboxTimeAggField.setAccessible(true)
    (
      lookup.unreflectGetter(mailboxTimesField),
      lookup.unreflectSetter(mailboxTimesField),
      lookup.unreflectGetter(mailboxTimeAggField),
      lookup.unreflectSetter(mailboxTimeAggField)
    )
  }

  @inline def setTimes(actorCell: Object): Unit =
    mailboxTimesSetterHandler.invoke(actorCell, mutable.ArrayBuffer.empty[MailboxTime])

  @inline def addTime(actorCell: Object, time: FiniteDuration): Unit =
    mailboxTimes(actorCell).foreach(times => checkSizeToAgg(actorCell, times += MailboxTime(time)))

  @inline private def checkSizeToAgg(actorCell: Object, times: MailboxTimesType): Unit =
    if (times.size >= AggSizeToAgg) {
      aggregate(actorCell, times.toArray)
      clearTimes(times)
    }

  /**
   * Get current mailbox time aggregation.
   * If aggregation is absent, this holder computes it and return it if possible.
   * If aggregation is present, this holder clears after return it to force an refresh in a next get.
   * If no time register is present, this holder returns None.
   */
  @inline def getAgg(actorCell: Object): Option[MailboxTimeAgg] =
    mailboxTimeAgg(actorCell).map { agg =>
      clearAgg(actorCell)
      agg
    }.orElse(aggregate(actorCell))

  @inline private def aggregate(actorCell: Object): Option[MailboxTimeAgg] =
    takeTimes(actorCell)
      .filter(_.nonEmpty)
      .map(times => aggregate(actorCell, times))

  @inline private def aggregate(actorCell: Object, times: Array[MailboxTime]): MailboxTimeAgg = {
    val agg = LongValueAggMetric.fromTimeSeries(new LongTimeSeries(times))
    mailboxTimeAggSetterHandler.invoke(actorCell, agg)
    agg
  }

  @inline private def takeTimes(actorCell: Object): Option[Array[MailboxTime]] = {
    val times = getTimes(actorCell)
    clearTimes(actorCell)
    times
  }

  @inline private def getTimes(actorCell: Object): Option[Array[MailboxTime]] =
    mailboxTimes(actorCell).map(_.toArray)

  @inline private def clearTimes(actorCell: Object): Unit =
    mailboxTimes(actorCell).foreach(clearTimes)

  @inline private def clearTimes(times: MailboxTimesType): Unit = times.clear()

  @inline private def clearAgg(actorCell: Object): Unit =
    mailboxTimeAggSetterHandler.invoke(actorCell, null)

  @inline private def mailboxTimes(actorCell: Object): Option[MailboxTimesType] =
    Option(mailboxTimesGetterHandler.invoke(actorCell)).map(_.asInstanceOf[MailboxTimesType])

  @inline private def mailboxTimeAgg(actorCell: Object): Option[MailboxTimeAgg] =
    Option(mailboxTimeAggGetterHandler.invoke(actorCell)).map(_.asInstanceOf[MailboxTimeAgg])

}
