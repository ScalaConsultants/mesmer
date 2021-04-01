package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.core.actor.ActorCellDecorator

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onEnter(@This actorCell: Object): Unit =
    ActorCellDecorator.initialize(actorCell)

}
