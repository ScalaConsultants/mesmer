package io.scalac.agent.akka.persistence

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted
import net.bytebuddy.asm.Advice

import java.lang.invoke.{ MethodHandles, MethodType }

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {
  import AkkaPersistenceAgent.logger

  private val replayingSnapshotClass = Class.forName("akka.persistence.typed.internal.ReplayingSnapshot")

  private val behaviorSetupClass = Class.forName("akka.persistence.typed.internal.BehaviorSetup")

  private val handle = {
    val lookup = MethodHandles
      .lookup()

    import MethodHandles._
    import MethodType._

    val persistenceId =
      lookup.findVirtual(behaviorSetupClass, "persistenceId", methodType(classOf[PersistenceId]))
    val behavior = lookup.findVirtual(replayingSnapshotClass, "setup", methodType(behaviorSetupClass))

    foldArguments(dropArguments(persistenceId, 1, replayingSnapshotClass), behavior)
  }

  def getPersistenceId(ref: AnyRef): PersistenceId = handle.invoke(ref).asInstanceOf[PersistenceId]
  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) context: ActorContext[_],
    @Advice.This thiz: AnyRef
  ): Unit = {
    val path = context.self.path
    logger.trace("Started actor {} recovery", path)
    val persistenceId = getPersistenceId(thiz).id

    EventBus(context.system)
      .publishEvent(RecoveryStarted(path.toString, persistenceId, Timestamp.create()))

  }
}
