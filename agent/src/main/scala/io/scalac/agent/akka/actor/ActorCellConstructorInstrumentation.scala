package io.scalac.agent.akka.actor

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

class ActorCellConstructorInstrumentation
object ActorCellConstructorInstrumentation {

  @OnMethodExit
  def onExit(@This actorCell: Object): Unit =
    MailboxTimesHolder.setTimes(actorCell)

}
