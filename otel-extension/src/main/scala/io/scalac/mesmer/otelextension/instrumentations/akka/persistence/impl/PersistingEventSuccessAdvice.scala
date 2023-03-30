package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodEnter

import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.PersistenceEvent.PersistingEventFinished
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.PersistenceService.persistenceService

object PersistingEventSuccessAdvice {

  @OnMethodEnter
  def onWriteSuccess(@Argument(0) context: ActorContext[_], @Argument(1) event: PersistentRepr): Unit = {
    val path: Path = context.self.path.toPath

    EventBus(context.system)
      .publishEvent(
        PersistingEventFinished(
          path,
          event.persistenceId,
          event.sequenceNr,
          Timestamp.create()
        )
      )
  }
}
