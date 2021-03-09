package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ MailboxTimeHolder, MessagesCountersHolder }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit =
    Option(actorCell).foreach { ac =>
      MailboxTimeHolder.setAggregator(ac)
      MessagesCountersHolder.setCounters(ac)
    }

}
