package io.scalac.agent.akka.persistence

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryFinished
import net.bytebuddy.asm.Advice

import java.lang.invoke.{ MethodHandles, MethodType }

class RecoveryCompletedInterceptor

object RecoveryCompletedInterceptor {

  private val replayingEventsClass = Class.forName("akka.persistence.typed.internal.ReplayingEvents")

  private val behaviorSetupClass = Class.forName("akka.persistence.typed.internal.BehaviorSetup")

  private val handle = {
    val lookup = MethodHandles
      .lookup()

    import MethodHandles._
    import MethodType._

    val persistenceId =
      lookup.findVirtual(behaviorSetupClass, "persistenceId", methodType(classOf[PersistenceId]))
    val behavior = lookup.findVirtual(replayingEventsClass, "setup", methodType(behaviorSetupClass))

    foldArguments(dropArguments(persistenceId, 1, replayingEventsClass), behavior)
  }

  def getPersistenceId(ref: AnyRef): PersistenceId = handle.invoke(ref).asInstanceOf[PersistenceId]

  import AkkaPersistenceAgent.logger
  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) actorContext: ActorContext[_],
    @Advice.This thiz: AnyRef
  ): Unit = {
    val path = actorContext.self.path
    logger.trace("Recovery completed for {}", path)

    val persistenceId = getPersistenceId(thiz).id
    EventBus(actorContext.system)
      .publishEvent(RecoveryFinished(path.toString, persistenceId, Timestamp.create()))

  }
}
