package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice._

import io.scalac.extension.actor.ActorCellDecorator

class ActorCellReceiveMessageInstrumentation
object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: Object): Unit =
    ActorCellDecorator.get(actorCell).foreach { spy =>
      spy.receivedMessages.inc()
      spy.processingTimer.start()
    }

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: Object, @Thrown exception: Throwable): Unit =
    ActorCellDecorator.get(actorCell).foreach { spy =>
      if (exception != null && !spy.exceptionHandledMarker.checkAndReset()) {
        spy.failedMessages.inc()
      }
      spy.processingTimeAgg.add(spy.processingTimer.interval())
    }

}
