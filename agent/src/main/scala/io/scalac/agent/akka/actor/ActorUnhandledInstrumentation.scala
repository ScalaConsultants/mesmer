package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.ActorCountsDecorators

class ActorUnhandledInstrumentation
object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit =
    ActorCountsDecorators.UnhandledAtActor.inc(actor)

}
