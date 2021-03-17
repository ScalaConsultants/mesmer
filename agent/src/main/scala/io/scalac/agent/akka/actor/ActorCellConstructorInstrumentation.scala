package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit = {
    ActorTimesDecorators.MailboxTime.initialize(actorCell)
    ActorTimesDecorators.ProcessingTime.initialize(actorCell)
    ActorTimesDecorators.ProcessingTimeSupport.initialize(actorCell)
    ActorCountsDecorators.Received.initialize(actorCell)
    ActorCountsDecorators.Unhandled.initialize(actorCell)
    ActorCountsDecorators.Failed.initialize(actorCell)
    ActorCountsDecorators.FailHandled.initialize(actorCell)
  }

}
