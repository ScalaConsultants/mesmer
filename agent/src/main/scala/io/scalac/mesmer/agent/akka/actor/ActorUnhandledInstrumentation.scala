package io.scalac.mesmer.agent.akka.actor

import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.extension.actor.ActorCellDecorator

object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit =
    ActorCellDecorator.get(ClassicActorOps.getContext(actor)).foreach(_.unhandledMessages.inc())

}
