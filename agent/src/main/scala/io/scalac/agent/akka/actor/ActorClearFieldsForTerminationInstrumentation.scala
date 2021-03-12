package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ FieldValue, OnMethodExit, Return, This }

import io.scalac.extension.actor.ActorCountsDecorators

class ActorClearFieldsForTerminationInstrumentation
object ActorClearFieldsForTerminationInstrumentation {

  @OnMethodExit
  def onExit(@This actorCell: Object, @FieldValue("_actor") actor: Object): Unit = {
    val currentCount = ActorCountsDecorators.UnhandledAtActor.getValue(actor).getOrElse(0L)
    ActorCountsDecorators.UnhandledAtCell.set(actorCell, currentCount)
  }

}
