package io.scalac.agent

import java.lang.reflect.Method

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import io.scalac.`extension`.{AgentListenerActorMessage, persistenceService}
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

    val actorPath      = actorContext.self.path.toStringWithoutAddress
    val recoveryTimeMs = System.currentTimeMillis - AkkaPersistenceAgentState.recoveryStarted.get(actorPath)
    println("Recovery took " + recoveryTimeMs + "ms for actor " + actorPath)

    (actorContext.system.receptionist ? Receptionist.Find(persistenceService))
      .map(_.serviceInstances(persistenceService).head)
      .filter(_.path.address.hasLocalScope) // take only local actorRefs
      .foreach(
        _ ! AgentListenerActorMessage.PersistentActorRecoveryDuration(actorContext.self.path, recoveryTimeMs)
      )

    AkkaPersistenceAgentState.recoveryStarted.remove(actorPath)
    AkkaPersistenceAgentState.recoveryMeasurements.put(actorPath, recoveryTimeMs)
  }
}
