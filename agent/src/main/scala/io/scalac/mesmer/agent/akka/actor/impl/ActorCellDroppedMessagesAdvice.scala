package io.scalac.mesmer.agent.akka.actor.impl

import akka.dispatch.MailboxType
import akka.dispatch.SingleConsumerOnlyUnboundedMailbox
import akka.dispatch.UnboundedMailbox
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.actor.ActorCellDecorator

object ActorCellDroppedMessagesAdvice {

  @OnMethodExit
  def init(@This actorCell: Object, @Argument(1) mailboxType: MailboxType): Unit =
    mailboxType match {
      case _: UnboundedMailbox | _: SingleConsumerOnlyUnboundedMailbox =>

      case _ =>
        ActorCellDecorator.get(actorCell).foreach { metrics =>
          metrics.initDroppedMessages()
        }
    }

}
