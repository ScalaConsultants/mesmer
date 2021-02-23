package io.scalac.agent.akka.actor

import scala.concurrent.duration._

import akka.actor.typed.scaladsl.adapter._

import net.bytebuddy.asm.Advice._

import io.scalac.core.util.{ ActorPathOps, Timestamp }
import io.scalac.extension.event.ActorEvent.MailboxTime
import io.scalac.extension.event.EventBus

class MailboxDequeueInstrumentation
object MailboxDequeueInstrumentation {

  @OnMethodExit
  def onExit(@Return envelope: Object, @This mailbox: Object): Unit =
    Option(envelope).map(computeTime).foreach(publish(mailbox))

  @inline def computeTime(envelope: Object): FiniteDuration =
    new FiniteDuration(Timestamp.create().interval(EnvelopeOps.getTimestamp(envelope)), NANOSECONDS)

  @inline def publish(mailbox: Object)(time: FiniteDuration): Unit = {
    val actor  = MailboxOps.getActor(mailbox)
    val system = ActorCellOps.getSystem(actor)
    val ref    = ActorCellOps.getSelf(actor)
    EventBus(system.toTyped).publishEvent(MailboxTime(time, ActorPathOps.getPathString(ref)))
  }

}
