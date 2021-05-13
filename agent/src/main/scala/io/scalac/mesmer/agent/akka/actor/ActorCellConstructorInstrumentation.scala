package io.scalac.mesmer.agent.akka.actor

import akka.dispatch.MailboxType
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{ Argument, OnMethodExit, This }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object, @Argument(1) mailboxType: MailboxType): Unit =
    ActorCellDecorator.initialize(actorCell, mailboxType)
}

object ActorCellDispatcherInit
