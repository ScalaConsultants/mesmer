package io.scalac.mesmer.agent.akka.persistence.impl

import java.lang.invoke.MethodHandle

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.core.util.Timestamp

object RecoveryCompletedAdvice {

  @OnMethodEnter
  def enter(
    @Argument(0) actorContext: ActorContext[_],
    @This self: AnyRef
  ): Unit = {
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
