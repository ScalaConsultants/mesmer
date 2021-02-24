package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

object MailboxTimesHolder {

  private lazy val holderClass = Class.forName("akka.actor.ActorCell")

  type MailboxTimesType = mutable.ArrayBuffer[MailboxTime]

  val MailboxTimesVar = "mailboxTimes"

  private lazy val lookup = MethodHandles.publicLookup()

  private lazy val mailboxTimesGetterHandler =
    lookup.findGetter(holderClass, MailboxTimesVar, classOf[MailboxTimesType])

  private lazy val mailboxTimesSetterHandler =
    lookup.findSetter(holderClass, MailboxTimesVar, classOf[MailboxTimesType])

  @inline def setTimes(actorCell: Object): Unit =
    mailboxTimesSetterHandler.invoke(actorCell, mutable.ArrayBuffer.empty[MailboxTime])

  @inline def addTime(actorCell: Object, time: FiniteDuration): Unit =
    mailboxTimes(actorCell) += MailboxTime(time)

  @inline def getTimes(actorCell: Object): Array[MailboxTime] =
    mailboxTimes(actorCell).toArray

  @inline def clearTimes(actorCell: Object): Unit =
    mailboxTimes(actorCell).clear()

  @inline private def mailboxTimes(mailbox: Object): MailboxTimesType =
    mailboxTimesGetterHandler.invoke(mailbox).asInstanceOf[MailboxTimesType]

}
