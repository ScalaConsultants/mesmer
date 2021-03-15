package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ MailboxTimeDecorator, MessageCounterDecorators }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit =
    if (actorCell != null) {
      MailboxTimeDecorator.setAggregator(actorCell)
      MessageCounterDecorators.Received.initialize(actorCell)
      MessageCounterDecorators.Unhandled.initialize(actorCell)
      MessageCounterDecorators.Failed.initialize(actorCell)
    }

}
