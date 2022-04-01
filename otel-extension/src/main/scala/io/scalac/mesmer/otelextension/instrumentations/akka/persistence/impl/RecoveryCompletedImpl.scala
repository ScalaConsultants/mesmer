package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.typed.PersistenceId
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.util.{ ReflectionFieldUtils, Timestamp }

import java.lang.invoke.MethodHandle
import io.scalac.mesmer.core.model._

object RecoveryCompletedImpl {

  def enter(
    actorContext: ActorContext[_],
    self: AnyRef
  ): Unit = {
    println("RECOVERY COMPLETED EXTENSION")

    val path = actorContext.self.path.toPath

    val replayingEventsSetupGetter: MethodHandle =
      ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.ReplayingEvents", "setup")

    val behaviorSetupPersistenceId: MethodHandle =
      ReflectionFieldUtils.getGetter("akka.persistence.typed.internal.BehaviorSetup", "persistenceId")

    val persistenceIdHandle =
      ReflectionFieldUtils.chain(replayingEventsSetupGetter, behaviorSetupPersistenceId)

    val persistenceId = persistenceIdHandle.invoke(self).asInstanceOf[PersistenceId]

    EventBus(actorContext.system)
      .publishEvent(
        RecoveryFinished(path, persistenceId.id, Timestamp.create())
      )

  }
}
