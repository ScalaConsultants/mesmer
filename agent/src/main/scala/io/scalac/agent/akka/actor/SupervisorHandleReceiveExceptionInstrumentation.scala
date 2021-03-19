package io.scalac.agent.akka.actor

import akka.actor.typed.TypedActorContext

import net.bytebuddy.asm.Advice.{ Argument, OnMethodExit }

import io.scalac.extension.actor.ActorCellSpy

class SupervisorHandleReceiveExceptionInstrumentation
object SupervisorHandleReceiveExceptionInstrumentation {

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@Argument(0) context: TypedActorContext[_]): Unit =
    ActorCellSpy.get(ClassicActorContextProviderOps.classicActorContext(context)).foreach { spy =>
      spy.failedMessages.inc()
      spy.exceptionHandledMarker.mark()
    }

}
