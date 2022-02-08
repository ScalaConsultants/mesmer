package io.scalac.mesmer.agent.akka.persistence.impl

import java.lang.invoke.MethodHandle

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import net.bytebuddy.asm.Advice

import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.core.util.Timestamp

class RecoveryStartedAdvice

object RecoveryStartedAdvice {

  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) context: ActorContext[_],
    @Advice.This self: AnyRef
  ): Unit = {
    val path = context.self.path.toPath

    val replayingSnapshotsSetupGetter: MethodHandle =
      ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.ReplayingSnapshot", "setup")

    val behaviorSetupPersistenceId: MethodHandle =
      ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.BehaviorSetup", "persistenceId")

    val persistenceIdHandle: MethodHandle =
      ReflectionFieldUtils.chain(replayingSnapshotsSetupGetter, behaviorSetupPersistenceId)

    val persistenceId = persistenceIdHandle.invoke(self).asInstanceOf[PersistenceId]

    EventBus(context.system).publishEvent(RecoveryStarted(path, persistenceId.id, Timestamp.create()))
  }
}
