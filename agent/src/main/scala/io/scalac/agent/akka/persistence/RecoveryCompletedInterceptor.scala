package io.scalac.agent.akka.persistence

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.util.Timeout
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent._
import net.bytebuddy.asm.Advice

import scala.concurrent.duration._

class RecoveryCompletedInterceptor

object RecoveryCompletedInterceptor {
  import AkkaPersistenceAgent.logger
  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) actorContext: ActorContext[_]
  ): Unit = {
    val path = actorContext.self.path
    logger.trace("Recovery completed for {}", path)
    implicit val ec        = actorContext.system.executionContext
    implicit val scheduler = actorContext.system.scheduler

    implicit val timeout   = Timeout(1.second)
    EventBus(actorContext.system)
      .publishEvent(RecoveryFinished(path, System.currentTimeMillis()))
  }
}
