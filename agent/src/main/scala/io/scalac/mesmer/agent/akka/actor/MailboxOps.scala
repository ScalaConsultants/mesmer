package io.scalac.mesmer.agent.akka.actor

import io.scalac.mesmer.core.util.ReflectionFieldUtils

object MailboxOps {

  private lazy val actorGetterHandler = ReflectionFieldUtils.getGetter("akka.dispatch.Mailbox", "actor")

  @inline final def getActor(mailbox: Object): Object =
    actorGetterHandler.invoke(mailbox)

}
