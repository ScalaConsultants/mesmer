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
    val a        = parameters(0).asInstanceOf[ActorContext[_]]
    val p        = a.self.path.toStringWithoutAddress
    val measured = System.currentTimeMillis - AkkaPersistenceAgentState.recoveryStarted.get(p)
    System.out.println("Recovery took " + measured + "ms for actor " + p)
    AkkaPersistenceAgentState.recoveryMeasurements.put(p, measured)
  }
}
