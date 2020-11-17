package io.scalac.agent

import java.lang.reflect.Method

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.util.Timeout
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent._
import net.bytebuddy.asm.Advice

import scala.concurrent.duration._

class RecoveryCompletedInterceptor

object RecoveryCompletedInterceptor {

  @Advice.OnMethodEnter
  def enter(
    @Advice.Origin method: Method,
    @Advice.AllArguments parameters: Array[Object],
    @Advice.This thiz: Object
  ): Unit = {
    println("Recovery completion intercepted. Method: " + method + ", This: " + thiz)
    val actorContext       = parameters(0).asInstanceOf[ActorContext[_]]
    implicit val ec        = actorContext.system.executionContext
    implicit val scheduler = actorContext.system.scheduler
    implicit val timeout   = Timeout(1.second)

    EventBus(actorContext.system)
      .publishEvent(RecoveryFinished(actorContext.self.path, System.currentTimeMillis()))
  }
}
