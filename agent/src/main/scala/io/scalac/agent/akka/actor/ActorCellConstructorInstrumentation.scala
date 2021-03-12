package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit =
    if (actorCell != null) {
      ActorTimesDecorators.MailboxTime.setAggregator(actorCell)
      ActorTimesDecorators.ProcessingTime.setAggregator(actorCell)
      ActorCountsDecorators.Received.initialize(actorCell)
      ActorCountsDecorators.UnhandledAtCell.initialize(actorCell)
      ActorCountsDecorators.Failed.initialize(actorCell)
    }

}
