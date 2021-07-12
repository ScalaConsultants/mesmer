package io.scalac.mesmer.agent.akka.actor.impl

import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.actor.ActorCellDecorator

object ActorUnhandledInstrumentation {

  @OnMethodExit
  def onExit(@This actor: Object): Unit =
    ActorCellDecorator
      .get(ClassicActorOps.getContext(actor))
      .foreach { metrics =>
        if (metrics.unhandledMessages.isDefined) {
          metrics.unhandledMessages.get.inc()
        }
      }

}
