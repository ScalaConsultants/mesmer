package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, Return, This }

import io.scalac.extension.actor.ActorCountsDecorators

class ActorNewActorInstrumentation
object ActorNewActorInstrumentation {

  @OnMethodExit
  def onExit(@This actorCell: Object, @Return actor: Object): Unit = {
    val currentCount = ActorCountsDecorators.UnhandledAtCell.getValue(actorCell).getOrElse(0L)
    ActorCountsDecorators.UnhandledAtActor.initialize(actor, currentCount)
  }

}
