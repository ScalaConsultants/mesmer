package io.scalac.agent.akka.actor

import scala.concurrent.duration._

import net.bytebuddy.asm.Advice._

import io.scalac.core.actor.ActorCellDecorator

class MailboxDequeueInstrumentation
object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Object, @This mailbox: Object): Unit =
    if (envelope != null) {
      add(mailbox, computeTime(envelope))
    }

  @inline final def computeTime(envelope: Object): FiniteDuration =
    EnvelopeDecorator.getTimestamp(envelope).interval().milliseconds

  @inline final def add(mailbox: Object, time: FiniteDuration): Unit =
    ActorCellDecorator.get(MailboxOps.getActor(mailbox)).foreach(_.mailboxTimeAgg.add(time))

}
