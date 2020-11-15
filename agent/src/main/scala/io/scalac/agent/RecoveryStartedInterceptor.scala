package io.scalac.agent

import java.lang.reflect.Method

import akka.actor.typed.scaladsl.ActorContext
import io.scalac.agent.event.EventBus
import io.scalac.extension.event.PersistenceEvent._
import net.bytebuddy.asm.Advice

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {

  @Advice.OnMethodEnter
  def enter(
    @Advice.Origin method: Method,
    @Advice.AllArguments parameters: Array[Object],
    @Advice.This thiz: Object
  ): Unit = {
    System.out.println("Recovery startup intercepted. Method: " + method + ", This: " + thiz)
    val context = parameters(0).asInstanceOf[ActorContext[_]]
    EventBus(context.system).publishEvent(RecoveryStarted(context.self.path, System.currentTimeMillis()))
  }
}
