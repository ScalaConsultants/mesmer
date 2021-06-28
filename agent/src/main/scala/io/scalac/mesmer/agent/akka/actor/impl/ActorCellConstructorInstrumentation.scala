package io.scalac.mesmer.agent.akka.actor.impl

import akka.dispatch.MailboxType
import io.scalac.mesmer.core.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object, @Argument(1) mailboxType: MailboxType): Unit =
    ActorCellDecorator.initialize(actorCell, mailboxType)
}
