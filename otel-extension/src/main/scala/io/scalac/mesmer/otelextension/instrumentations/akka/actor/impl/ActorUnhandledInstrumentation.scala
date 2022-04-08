package io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl

import akka.actor.ActorContext
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics

object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit = {
    val context: ActorContext             = ClassicActorOps.getContext(actor)
    val metrics: Option[ActorCellMetrics] = ActorCellDecorator.getMetrics(context)

    metrics.foreach { metrics =>
      if (metrics.unhandledMessages.isDefined) {
        metrics.unhandledMessages.get.inc()
      }
    }
  }

}
