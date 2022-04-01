package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import _root_.akka.persistence.typed.PersistenceId
import akka.actor.typed.scaladsl.ActorContext
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.{ReflectionFieldUtils, Timestamp}

import java.lang.invoke.MethodHandle

object RecoveryStartedImpl {

  def enter(
    context: ActorContext[_],
    self: AnyRef
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
