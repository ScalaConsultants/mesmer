package io.scalac.mesmer.agent.akka.actor

import akka.actor.typed.TypedActorContext
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice.{ Argument, OnMethodExit }

class SupervisorHandleReceiveExceptionInstrumentation
object SupervisorHandleReceiveExceptionInstrumentation {

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@Argument(0) context: TypedActorContext[_]): Unit =
    ActorCellDecorator.get(ClassicActorContextProviderOps.classicActorContext(context)).foreach { actorMetrics =>
      actorMetrics.failedMessages.inc()
      actorMetrics.exceptionHandledMarker.mark()
    }

}
