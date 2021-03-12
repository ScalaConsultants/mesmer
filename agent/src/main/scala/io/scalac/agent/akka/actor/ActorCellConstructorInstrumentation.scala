package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ MailboxTimeDecorators, MessageCounterDecorators }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit =
    if (actorCell != null) {
      MailboxTimeDecorators.MailboxTime.setAggregator(actorCell)
      MailboxTimeDecorators.ProcessingTime.setAggregator(actorCell)
      MessageCounterDecorators.Received.initialize(actorCell)
      MessageCounterDecorators.UnhandledAtCell.initialize(actorCell)
      MessageCounterDecorators.Failed.initialize(actorCell)
    }

}
