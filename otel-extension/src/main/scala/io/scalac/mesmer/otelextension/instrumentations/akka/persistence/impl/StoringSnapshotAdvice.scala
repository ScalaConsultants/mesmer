package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.SaveSnapshotSuccess
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodEnter
import net.bytebuddy.asm.Advice.This

import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.ReflectionFieldUtils
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.PersistenceEvent.SnapshotCreated
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.PersistenceService.persistenceService

object StoringSnapshotAdvice {

  @OnMethodEnter
  def onSaveSnapshotResponse(
    @Argument(0) response: AnyRef,
    @This self: AbstractBehavior[_]
  ): Unit = {

    val contextGetter = ReflectionFieldUtils.getGetter("akka.actor.typed.scaladsl.AbstractBehavior", "context")

    val context = contextGetter.invoke(self).asInstanceOf[ActorContext[_]]
    response match {
      case SaveSnapshotSuccess(meta) =>
        EventBus(context.system)
          .publishEvent(
            SnapshotCreated(
              context.self.path.toPath,
              meta.persistenceId,
              meta.sequenceNr,
              Timestamp.create()
            )
          )
      case _ =>
    }
  }
}
