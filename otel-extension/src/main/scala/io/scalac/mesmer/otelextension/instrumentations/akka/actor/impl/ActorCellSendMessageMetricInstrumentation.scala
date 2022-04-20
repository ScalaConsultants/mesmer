package io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl

import akka.actor.Actor
import akka.dispatch.Envelope
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.util.ActorRefOps
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.EnvelopeDecorator

object ActorCellSendMessageMetricInstrumentation {

  @OnMethodEnter
  def onEnter(@Argument(0) envelope: Object): Unit =
    if (envelope != null) {
      val sender = EnvelopeOps.getSender(envelope)
      if (sender != Actor.noSender)
        for {
          cell    <- ActorRefOps.Local.cell(sender)
          metrics <- ActorCellDecorator.getMetrics(cell) if metrics.sentMessages.isDefined
        } metrics.sentMessages.get.inc()
    }
}

object ActorCellSendMessageTimestampInstrumentation {
  @OnMethodEnter
  def onEnter(@Argument(0) envelope: Envelope): Unit =
    if (envelope != null) {
      EnvelopeDecorator.setCurrentTimestamp(envelope)
    }
}
