package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.core.actor.ActorCellDecorator

class ActorUnhandledInstrumentation
object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit =
    ActorCellDecorator.get(ClassicActorOps.getContext(actor)).foreach(_.unhandledMessages.inc())

}
