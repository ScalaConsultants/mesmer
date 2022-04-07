package io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl

import akka.dispatch.Envelope
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.Return
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics
import io.scalac.mesmer.core.util.Interval
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.EnvelopeDecorator

object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Envelope, @This mailbox: Object): Unit =
    if (envelope != null) {
      ActorCellDecorator.getMetrics(MailboxOps.getActor(mailbox)).foreach { metrics =>
        if (metrics.mailboxTimeAgg.isDefined) {
          add(metrics, computeTime(envelope))
        }
      }
    }

  @inline private def computeTime(envelope: Envelope): Interval = EnvelopeDecorator.getInterval(envelope)

  @inline private def add(metrics: ActorCellMetrics, time: Interval): Unit =
    metrics.mailboxTimeAgg.get.add(time)

}
