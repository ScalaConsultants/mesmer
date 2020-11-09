package io.scalac.agent

import java.lang.reflect.Method

import akka.actor.typed.scaladsl.ActorContext
import net.bytebuddy.asm.Advice;

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {

  @Advice.OnMethodEnter
  def enter(
    @Advice.Origin method: Method,
    @Advice.AllArguments parameters: Array[Object],
    @Advice.This thiz: Object
  ): Unit = {
    System.out.println("Recovery startup intercepted. Method: " + method + ", This: " + thiz)
    val actorPath = parameters(0).asInstanceOf[ActorContext[_]].self.path.toStringWithoutAddress
    System.out.println("Recovery startup actor path: " + actorPath)
    AkkaPersistenceAgentState.recoveryStarted.put(actorPath, System.currentTimeMillis())
  }
}
