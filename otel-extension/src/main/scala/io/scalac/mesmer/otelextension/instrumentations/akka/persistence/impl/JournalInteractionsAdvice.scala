package io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.mesmer.core.util.Timestamp
import net.bytebuddy.asm.Advice.{ Argument, OnMethodEnter }
import io.scalac.mesmer.core.model._


object JournalInteractionsAdvice {

  @OnMethodEnter
  def onWriteInitiated(@Argument(0) context: ActorContext[_], @Argument(2) event: PersistentRepr): Unit = {
    val path = context.self.path.toPath
    EventBus(context.system)
      .publishEvent(
        PersistingEventStarted(
          path,
          event.persistenceId,
          event.sequenceNr,
          Timestamp.create()
        )
      )
  }

}
