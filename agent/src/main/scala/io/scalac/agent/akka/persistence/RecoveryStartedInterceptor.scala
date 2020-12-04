package io.scalac.agent.akka.persistence

import java.lang.reflect.Method

import _root_.akka.actor.typed.scaladsl.ActorContext
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent._
import net.bytebuddy.asm.Advice

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {
  import AkkaPersistenceAgent.logger

  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) context: ActorContext[_],
  ): Unit = {
    logger.trace("Started actor {} recovery", context.self.path)
    EventBus(context.system).publishEvent(RecoveryStarted(context.self.path, System.currentTimeMillis()))
  }
}
