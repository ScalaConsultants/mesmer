package io.scalac.mesmer.agent.akka.actor.impl

import akka.MesmerMirrorTypes.Cell

import io.scalac.mesmer.core.util.ReflectionFieldUtils

object MailboxOps {

  private lazy val actorGetterHandler = ReflectionFieldUtils.getGetter("akka.dispatch.Mailbox", "actor")

  @inline final def getActor(mailbox: Object): Cell =
    actorGetterHandler.invoke(mailbox)

}
