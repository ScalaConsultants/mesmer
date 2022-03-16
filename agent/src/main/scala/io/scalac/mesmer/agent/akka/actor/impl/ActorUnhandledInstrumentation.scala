package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.ActorContext
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.agent.akka.actor.ActorInstruments
import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics

object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit = {
    val context: ActorContext             = ClassicActorOps.getContext(actor)
    val metrics: Option[ActorCellMetrics] = ActorCellDecorator.getMetrics(context)

    val attr = ActorCellDecorator.getCellAttributes(context)
    ActorInstruments.unhandledMessages.add(1, attr)

    metrics.foreach { metrics =>
      if (metrics.unhandledMessages.isDefined) {
        metrics.unhandledMessages.get.inc()
      }
    }
  }

}
