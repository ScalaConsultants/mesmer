package io.scalac.agent.akka.actor

import akka.actor.typed.TypedActorContext
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodExit

import io.scalac.extension.actor.ActorCellDecorator

class SupervisorHandleReceiveExceptionInstrumentation
object SupervisorHandleReceiveExceptionInstrumentation {

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@Argument(0) context: TypedActorContext[_]): Unit =
    ActorCellDecorator.get(ClassicActorContextProviderOps.classicActorContext(context)).foreach { spy =>
      spy.failedMessages.inc()
      spy.exceptionHandledMarker.mark()
    }

}
