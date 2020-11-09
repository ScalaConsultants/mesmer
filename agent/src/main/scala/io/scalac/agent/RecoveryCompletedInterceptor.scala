package io.scalac.agent

import java.lang.reflect.Method

import akka.actor.typed.scaladsl.ActorContext
import net.bytebuddy.asm.Advice;

class RecoveryCompletedInterceptor

object RecoveryCompletedInterceptor {

  @Advice.OnMethodEnter
  def enter(
    @Advice.Origin method: Method,
    @Advice.AllArguments parameters: Array[Object],
    @Advice.This thiz: Object
  ): Unit = {
    System.out.println("Recovery completion intercepted. Method: " + method + ", This: " + thiz)
    val actorPath = parameters(0).asInstanceOf[ActorContext[_]].self.path.toStringWithoutAddress
    val recoveryTimeMs  = System.currentTimeMillis - AkkaPersistenceAgentState.recoveryStarted.get(actorPath)
    System.out.println("Recovery took " + recoveryTimeMs + "ms for actor " + actorPath)
    AkkaPersistenceAgentState.recoveryMeasurements.put(actorPath, recoveryTimeMs)
  }
}
