package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ MessagesCountersHolder, MessagesTimersHolder }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit =
    Option(actorCell).foreach { ac =>
      MessagesTimersHolder.MailboxTime.setAggregator(ac)
      MessagesCountersHolder.setCounters(ac)
    }

}
