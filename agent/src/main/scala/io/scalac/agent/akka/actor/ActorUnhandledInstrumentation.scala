package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.MessageCounterDecorators

class ActorUnhandledInstrumentation
object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit =
    MessageCounterDecorators.UnhandledAtActor.inc(actor)

}
