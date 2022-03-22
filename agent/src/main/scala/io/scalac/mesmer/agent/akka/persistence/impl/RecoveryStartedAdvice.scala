package io.scalac.mesmer.agent.akka.persistence.impl

import java.lang.invoke.MethodHandle

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.opentelemetry.instrumentation.api.field.VirtualField
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.agent.akka.persistence.RecoveryEventStartTime
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.core.util.Timestamp

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

    val startTime = Timestamp.create()

    // TODO: Not sure if we can attach this to ActorContext or something more specific is needed.
    //  Alternatively: use storages from the Akka extension (RecoveryStorage, CleanablePersistentStorage etc)???
    VirtualField
      .find(classOf[ActorContext[_]], classOf[RecoveryEventStartTime])
      .set(context, RecoveryEventStartTime(startTime))

    EventBus(context.system).publishEvent(RecoveryStarted(path, persistenceId.id, startTime))
  }
}
