package io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter.TypedActorContextOps
import net.bytebuddy.asm.Advice

import io.scalac.mesmer.core.actor.ActorCellDecorator

object StashBufferAdvice {

  @Advice.OnMethodExit
  def stash(
    @Advice.FieldValue("akka$actor$typed$internal$StashBufferImpl$$ctx") ctx: ActorContext[_]
  ): Unit =
    ActorCellDecorator.getMetrics(ctx.toClassic).foreach { metrics =>
      if (metrics.stashedMessages.isEmpty) {
        metrics.initStashedMessages()
      }
      metrics.stashedMessages.get.inc()
    }

}
