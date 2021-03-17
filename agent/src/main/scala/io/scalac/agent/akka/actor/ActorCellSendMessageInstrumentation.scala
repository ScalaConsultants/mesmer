package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice._

import io.scalac.core.util.ActorRefOps
import io.scalac.extension.actor.ActorCountsDecorators

class ActorCellSendMessageInstrumentation
object ActorCellSendMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@Argument(0) envelope: Object): Unit =
    if (envelope != null) {
      EnvelopeDecorator.setTimestamp(envelope)
      ActorRefOps.Local
        .cell(EnvelopeDecorator.getSender(envelope))
        .foreach(ActorCountsDecorators.Sent.inc)
    }

}
