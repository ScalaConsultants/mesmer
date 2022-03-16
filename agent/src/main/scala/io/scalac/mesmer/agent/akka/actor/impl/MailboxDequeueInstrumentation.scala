package io.scalac.mesmer.agent.akka.actor.impl

import akka.dispatch.Envelope
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.Return
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.agent.akka.actor.ActorInstruments
import io.scalac.mesmer.agent.akka.actor.EnvelopeDecorator
import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics
import io.scalac.mesmer.core.util.Interval

object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Envelope, @This mailbox: Object): Unit =
    if (envelope != null) {

      val cell       = MailboxOps.getActor(mailbox)
      val attributes = ActorCellDecorator.getCellAttributes(cell)

      val time: Interval = computeTime(envelope)
      ActorInstruments.mailboxTime.record(time.toNano, attributes)

      ActorCellDecorator.getMetrics(cell).foreach { metrics =>
        if (metrics.mailboxTimeAgg.isDefined) {
          add(metrics, computeTime(envelope))
        }
      }
    }

  @inline private def computeTime(envelope: Envelope): Interval = EnvelopeDecorator.getInterval(envelope)

  @inline private def add(metrics: ActorCellMetrics, time: Interval): Unit =
    metrics.mailboxTimeAgg.get.add(time)

}
