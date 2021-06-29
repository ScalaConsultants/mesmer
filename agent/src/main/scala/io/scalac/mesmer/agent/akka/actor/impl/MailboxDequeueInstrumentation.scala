package io.scalac.mesmer.agent.akka.actor.impl

import io.scalac.mesmer.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import io.scalac.mesmer.core.util.Interval
import net.bytebuddy.asm.Advice.{ OnMethodExit, Return, This }

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
