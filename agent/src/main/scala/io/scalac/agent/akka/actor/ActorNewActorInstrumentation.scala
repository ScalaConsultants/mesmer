package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, Return, This }

import io.scalac.extension.actor.MessageCounterDecorators

class ActorNewActorInstrumentation
object ActorNewActorInstrumentation {

  @OnMethodExit
  def onExit(@This actorCell: Object, @Return actor: Object): Unit = {
    val currentCount = MessageCounterDecorators.UnhandledAtCell.getValue(actorCell).getOrElse(0L)
    MessageCounterDecorators.UnhandledAtActor.initialize(actor, currentCount)
  }

}
