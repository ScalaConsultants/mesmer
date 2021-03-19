package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.ActorCellSpy

class ActorUnhandledInstrumentation
object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit =
    ActorCellSpy.get(ClassicActorOps.getContext(actor)).foreach(_.unhandledMessages.inc())

}
