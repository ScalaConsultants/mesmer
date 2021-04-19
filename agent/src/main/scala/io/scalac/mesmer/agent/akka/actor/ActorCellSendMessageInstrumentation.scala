package io.scalac.mesmer.agent.akka.actor

import akka.actor.Actor
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.core.util.ActorRefOps
import io.scalac.mesmer.extension.actor.ActorCellDecorator

class ActorCellSendMessageInstrumentation
object ActorCellSendMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@Argument(0) envelope: Object): Unit =
    if (envelope != null) {
      EnvelopeDecorator.setTimestamp(envelope)
      val sender = EnvelopeOps.getSender(envelope)
      if (sender != Actor.noSender)
        for {
          cell <- ActorRefOps.Local.cell(sender)
          spy  <- ActorCellDecorator.get(cell)
        } spy.sentMessages.inc()
    }

}
