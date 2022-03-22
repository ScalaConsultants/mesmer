package io.scalac.mesmer.agent.akka.persistence.impl

import java.lang.invoke.MethodHandle

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.api.field.VirtualField
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.agent.akka.persistence.PersistenceInstruments
import io.scalac.mesmer.agent.akka.persistence.RecoveryEventStartTime
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

    val recoveryFinishTimestamp = Timestamp.create()
    EventBus(actorContext.system)
      .publishEvent(
        RecoveryFinished(path, persistenceId.id, recoveryFinishTimestamp)
      )

    val recoveryStart: RecoveryEventStartTime =
      VirtualField.find(classOf[ActorContext[_]], classOf[RecoveryEventStartTime]).get(actorContext)

    val duration = Timestamp.interval(recoveryStart.startTime, recoveryFinishTimestamp)

    // TODO: I'm skipping the "node" attribute here, but afaik it was optional anyway.
    val attributes = Attributes
      .builder()
      .put("path", path)
      .put("persistence_id", persistenceId.id)
      .build()

    PersistenceInstruments.recoveryTotalCounter.add(1, attributes)
    PersistenceInstruments.recoveryTimeRecorder.record(duration.toMillis, attributes)

  }
}
