package io.scalac.mesmer.agent.akka.actor.impl

import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{ OnMethodEnter, OnMethodExit, This, Thrown }

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
