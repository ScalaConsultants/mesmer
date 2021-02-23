package io.scalac.agent.akka.actor

import java.lang.invoke.MethodHandles

object MailboxOps {

  private val lookup = MethodHandles.publicLookup()

  private val actorGetterHandler = {
    val field = Class.forName("akka.dispatch.Mailbox").getDeclaredField("actor")
    field.setAccessible(true)
    lookup.unreflectGetter(field)
  }

  def getActor(mailbox: Object): Object = actorGetterHandler.invoke(mailbox)

}
