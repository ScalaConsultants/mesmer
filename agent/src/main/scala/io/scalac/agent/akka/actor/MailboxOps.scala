package io.scalac.agent.akka.actor

import io.scalac.core.util.ReflectionFieldUtils

object MailboxOps {

  private lazy val actorGetterHandler = ReflectionFieldUtils.getGetter("akka.dispatch.Mailbox", "actor")

  @inline final def getActor(mailbox: Object): Object =
    actorGetterHandler.invoke(mailbox)

}
