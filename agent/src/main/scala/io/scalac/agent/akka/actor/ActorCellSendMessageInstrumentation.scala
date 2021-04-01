package io.scalac.agent.akka.actor

import akka.actor.Actor
import net.bytebuddy.asm.Advice._

import io.scalac.core.actor.ActorCellDecorator
import io.scalac.core.util.ActorRefOps

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
