package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.ActorContext
import net.bytebuddy.asm.Advice.OnMethodEnter
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This
import net.bytebuddy.asm.Advice.Thrown

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics

object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: ActorContext): Unit = {
    val cellMetrics: Option[ActorCellMetrics] = ActorCellDecorator.getMetrics(actorCell)

    cellMetrics.foreach { metrics =>
      import metrics._
      if (receivedMessages.isDefined) receivedMessages.get.inc()
      if (processingTimer.isDefined) processingTimer.get.start()
    }
  }

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: ActorContext, @Thrown exception: Throwable): Unit = {
    val cellMetrics: Option[ActorCellMetrics] = ActorCellDecorator.getMetrics(actorCell)

    cellMetrics.foreach { metrics =>
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
}
