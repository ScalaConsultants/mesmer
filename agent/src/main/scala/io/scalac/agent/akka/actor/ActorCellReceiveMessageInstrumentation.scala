package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodEnter, OnMethodExit, This, Thrown }

import io.scalac.extension.actor.ActorCountsDecorators

class ActorCellReceiveMessageInstrumentation
object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: Object): Unit =
    ActorCountsDecorators.Received.inc(actorCell)

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: Object, @Thrown exception: Throwable): Unit =
    if (exception != null) ActorCountsDecorators.Failed.inc(actorCell)

}
