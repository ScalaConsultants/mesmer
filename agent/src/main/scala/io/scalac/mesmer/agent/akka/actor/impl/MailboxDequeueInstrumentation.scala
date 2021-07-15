package io.scalac.mesmer.agent.akka.actor.impl

import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.Return
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics
import io.scalac.mesmer.core.util.Interval

object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Object, @This mailbox: Object): Unit =
    if (envelope != null) {
      ActorCellDecorator.get(MailboxOps.getActor(mailbox)).foreach { metrics =>
        if (metrics.mailboxTimeAgg.isDefined) {
          add(metrics, computeTime(envelope))
        }
      }
    }

  @inline private def computeTime(envelope: Object): Interval =
    EnvelopeDecorator.getTimestamp(envelope).interval()

  @inline private def add(metrics: ActorCellMetrics, time: Interval): Unit =
    metrics.mailboxTimeAgg.get.add(time)

}
