package io.scalac.agent.akka.actor

import scala.concurrent.duration._

import net.bytebuddy.asm.Advice._

import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.MailboxTimeDecorators

class MailboxDequeueInstrumentation
object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Object, @This mailbox: Object): Unit =
    if (envelope != null) {
      add(mailbox, computeTime(envelope))
    }

  @inline final def computeTime(envelope: Object): FiniteDuration =
    FiniteDuration(Timestamp.create().interval(EnvelopeOps.getTimestamp(envelope)), MILLISECONDS)

  @inline final def add(mailbox: Object, time: FiniteDuration): Unit =
    MailboxTimeDecorators.MailboxTime.addTime(MailboxOps.getActor(mailbox), time)

}
