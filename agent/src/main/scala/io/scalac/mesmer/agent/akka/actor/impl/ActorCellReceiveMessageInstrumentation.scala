package io.scalac.mesmer.agent.akka.actor.impl

import io.scalac.mesmer.core.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{ OnMethodEnter, OnMethodExit, This, Thrown }

object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: Object): Unit =
    ActorCellDecorator.get(actorCell).foreach { metrics =>
      import metrics._
      if (receivedMessages.isDefined) receivedMessages.get.inc()
      if (processingTimer.isDefined) processingTimer.get.start()
    }

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: Object, @Thrown exception: Throwable): Unit =
    ActorCellDecorator.get(actorCell).foreach { metrics =>
      import metrics._

      if (
        exception != null && exceptionHandledMarker.isDefined && failedMessages.isDefined && !exceptionHandledMarker.get
          .checkAndReset()
      ) {
        failedMessages.get.inc()
      }
      if (processingTimeAgg.isDefined && processingTimeAgg.isDefined) {
        processingTimeAgg.get.add(metrics.processingTimer.get.interval())
      }
    }

}
