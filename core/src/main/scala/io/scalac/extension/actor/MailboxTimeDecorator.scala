package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import scala.concurrent.duration.FiniteDuration

import io.scalac.extension.util.AggMetric.LongValueAggMetric
import io.scalac.extension.util.LongNoLockAggregator

object MailboxTimeDecorator {

  type MailboxTimeAgg = LongNoLockAggregator

  val MailboxTimeAggVar = "mailboxTimeAgg"

  private lazy val (
    mailboxTimeAggGetterHandler,
    mailboxTimeAggSetterHandler
  ) = {
    val actorCellClass = Class.forName("akka.actor.ActorCell")

    val mailboxTimeAggField = actorCellClass.getDeclaredField(MailboxTimeAggVar)
    mailboxTimeAggField.setAccessible(true)
    val lookup = MethodHandles.publicLookup()
    (
      lookup.unreflectGetter(mailboxTimeAggField),
      lookup.unreflectSetter(mailboxTimeAggField)
    )
  }

  @inline final def setAggregator(actorCell: Object): Unit =
    mailboxTimeAggSetterHandler.invoke(actorCell, new MailboxTimeAgg())

  @inline final def addTime(actorCell: Object, time: FiniteDuration): Unit =
    mailboxTimeAggGetterHandler.invoke(actorCell).asInstanceOf[MailboxTimeAgg].push(MailboxTime(time))

  @inline final def getMetrics(actorCell: Object): Option[LongValueAggMetric] =
    mailboxTimeAggGetterHandler.invoke(actorCell).asInstanceOf[LongNoLockAggregator].fetch()

}
