package io.scalac.agent.akka.actor
import net.bytebuddy.asm.Advice

class StashBufferAdvice
object StashBufferAdvice {

  @Advice.OnMethodExit
  def stash(): Unit = {}
}
