package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodEnter, OnMethodExit, This, Thrown }

import io.scalac.extension.actor.MessageCounterDecorators

class ActorCellReceiveMessageInstrumentation
object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: Object): Unit =
    MessageCounterDecorators.Received.inc(actorCell)

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: Object, @Thrown exception: Throwable): Unit =
    if (exception != null) MessageCounterDecorators.Failed.inc(actorCell)

}
