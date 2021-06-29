package io.scalac.mesmer.agent.akka.actor.impl

import akka.dispatch.{ MailboxType, SingleConsumerOnlyUnboundedMailbox, UnboundedMailbox }
import io.scalac.mesmer.core.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{ Argument, OnMethodExit, This }

object ActorCellDroppedMessagesAdvice {

  @OnMethodExit
  def init(@This actorCell: Object, @Argument(1) mailboxType: MailboxType): Unit =
    mailboxType match {
      case _: UnboundedMailbox | _: SingleConsumerOnlyUnboundedMailbox =>
        ActorCellDecorator.get(actorCell).foreach { metrics =>
          metrics.initDroppedMessages()
        }
      case _ =>
    }

}
