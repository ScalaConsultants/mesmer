package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import scala.concurrent.duration.FiniteDuration

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.LongNoLockAggregator

object MailboxTimeHolder {

  type MailboxTimeAgg = LongNoLockAggregator

  val MailboxTimeAggVar = "mailboxTimeAgg"

  private lazy val lookup = MethodHandles.publicLookup()

  private lazy val (
    mailboxTimeAggGetterHandler,
    mailboxTimeAggSetterHandler
  ) = {
    val actorCellClass = Class.forName("akka.actor.ActorCell")

    val mailboxTimeAggField = actorCellClass.getDeclaredField(MailboxTimeAggVar)
    mailboxTimeAggField.setAccessible(true)
    (
      lookup.unreflectGetter(mailboxTimeAggField),
      lookup.unreflectSetter(mailboxTimeAggField)
    )
  }

  @inline def setAggregator(actorCell: Object): Unit =
    mailboxTimeAggSetterHandler.invoke(actorCell, new MailboxTimeAgg())

  @inline def addTime(actorCell: Object, time: FiniteDuration): Unit =
    mailboxTimeAggGetterHandler.invoke(actorCell).asInstanceOf[MailboxTimeAgg].push(MailboxTime(time))

  @inline def getMetrics(actorCell: Object): Option[LongValueAggMetric] =
    mailboxTimeAggGetterHandler.invoke(actorCell).asInstanceOf[LongNoLockAggregator].fetch()

}
