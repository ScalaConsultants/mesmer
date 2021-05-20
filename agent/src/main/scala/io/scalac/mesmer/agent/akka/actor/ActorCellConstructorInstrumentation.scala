package io.scalac.mesmer.agent.akka.actor

import akka.dispatch.MailboxType
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.extension.actor.ActorCellDecorator

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object, @Argument(1) mailboxType: MailboxType): Unit =
    ActorCellDecorator.initialize(actorCell, mailboxType)
}

object ActorCellDispatcherInit
