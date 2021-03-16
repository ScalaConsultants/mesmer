package io.scalac.agent.akka.actor

import akka.actor.typed.TypedActorContext

import net.bytebuddy.asm.Advice.{ Argument, OnMethodEnter }

import io.scalac.extension.actor.MessageCounterDecorators

class AbstractSupervisionHandleReceiveExceptionInstrumentation
object AbstractSupervisionHandleReceiveExceptionInstrumentation {

  @OnMethodEnter
  def onEnter(@Argument(0) context: TypedActorContext[_]): Unit =
    MessageCounterDecorators.FailedAtSupervisor.inc(context)

}
