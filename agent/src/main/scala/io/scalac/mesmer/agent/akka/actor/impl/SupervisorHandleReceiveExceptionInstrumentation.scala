package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.typed.TypedActorContext
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodExit

import io.scalac.mesmer.core.actor.ActorCellDecorator

object SupervisorHandleReceiveExceptionInstrumentation {

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@Argument(0) context: TypedActorContext[_]): Unit =
    ActorCellDecorator.get(ClassicActorContextProviderOps.classicActorContext(context)).foreach { metrics =>
      import metrics._
      if (failedMessages.isDefined && exceptionHandledMarker.isDefined) {

        failedMessages.get.inc()
        exceptionHandledMarker.get.mark()

      }
    }

}
