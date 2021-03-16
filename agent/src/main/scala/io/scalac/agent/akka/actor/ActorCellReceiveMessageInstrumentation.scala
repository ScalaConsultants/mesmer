package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice._

import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }

class ActorCellReceiveMessageInstrumentation
object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: Object): Unit = {
    ActorCountsDecorators.Received.inc(actorCell)
    ActorTimesDecorators.ProcessingTimeSupport.set(actorCell)
  }

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: Object, @Thrown exception: Throwable): Unit = {
    if (exception != null) {
      ActorCountsDecorators.Failed.inc(actorCell)
    }
    ActorTimesDecorators.ProcessingTime.addTime(
      actorCell,
      ActorTimesDecorators.ProcessingTimeSupport.interval(actorCell)
    )
  }

}
