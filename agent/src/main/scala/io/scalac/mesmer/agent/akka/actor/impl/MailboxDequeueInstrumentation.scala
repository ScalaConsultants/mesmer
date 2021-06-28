package io.scalac.mesmer.agent.akka.actor.impl

import io.scalac.mesmer.core.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.Return
import net.bytebuddy.asm.Advice.This
import io.scalac.mesmer.core.util.Interval

object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Object, @This mailbox: Object): Unit =
    if (envelope != null) {
      add(mailbox, computeTime(envelope))
    }

  @inline final def computeTime(envelope: Object): Interval =
    EnvelopeDecorator.getTimestamp(envelope).interval()

  @inline final def add(mailbox: Object, time: Interval): Unit =
    ActorCellDecorator.get(MailboxOps.getActor(mailbox)).foreach(_.mailboxTimeAgg.add(time))

}
