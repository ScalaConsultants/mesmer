package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.actor.MailboxTimesHolder

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onExit(@This actorCell: Object): Unit =
    MailboxTimesHolder.setTimes(actorCell)

}
