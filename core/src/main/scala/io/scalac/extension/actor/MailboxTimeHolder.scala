package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import scala.concurrent.duration.FiniteDuration

import io.scalac.core.akka.model.MailboxTime

object MailboxTimeHolder {

  private lazy val holderClass = Class.forName("akka.actor.ActorCell")

  val MailboxTimesVar = "mailboxTimes"

  private lazy val lookup = MethodHandles.publicLookup()

  private lazy val mailboxTimeGetterHandler =
    lookup.findGetter(holderClass, MailboxTimesVar, classOf[MailboxTime])

  private lazy val mailboxTimeSetterHandler =
    lookup.findSetter(holderClass, MailboxTimesVar, classOf[MailboxTime])

  @inline def setTime(actorCell: Object, time: FiniteDuration): Unit =
    mailboxTimeSetterHandler.invoke(actorCell, time.toMillis)

  @inline def getTime(actorCell: Object): Option[MailboxTime] =
    Option(mailboxTimeGetterHandler.invoke(actorCell)).map(_.asInstanceOf[MailboxTime])

}
