package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import java.lang.invoke.MethodHandle

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.PersistenceId
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodEnter
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.PersistenceService.persistenceService

object RecoveryStartedAdvice {

  @OnMethodEnter
  def enter(
    @Argument(0) context: ActorContext[_],
    @This self: AnyRef
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
