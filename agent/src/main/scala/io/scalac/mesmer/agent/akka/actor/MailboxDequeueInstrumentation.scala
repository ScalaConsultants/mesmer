package io.scalac.mesmer.agent.akka.actor

import io.scalac.mesmer.core.util.Interval
import net.bytebuddy.asm.Advice._

import scala.concurrent.duration._
import io.scalac.mesmer.extension.actor.ActorCellDecorator

class MailboxDequeueInstrumentation
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
