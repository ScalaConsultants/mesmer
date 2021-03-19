package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.{ ActorCellSpy, ActorTimesDecorators }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit = {
    // TODO Aggregate all the decorators below into a single class and field inside actor cell.
    ActorTimesDecorators.ProcessingTimeSupport.initialize(actorCell)
    ActorCellSpy.initialize(actorCell)
  }

}
