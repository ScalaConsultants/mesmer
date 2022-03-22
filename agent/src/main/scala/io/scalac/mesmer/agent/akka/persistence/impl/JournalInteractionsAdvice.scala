package io.scalac.mesmer.agent.akka.persistence.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import io.opentelemetry.instrumentation.api.field.VirtualField
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.agent.akka.persistence.PersistenceEventStartTime
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp

object JournalInteractionsAdvice {

  @OnMethodEnter
  def onWriteInitiated(@Argument(0) context: ActorContext[_], @Argument(2) event: PersistentRepr): Unit = {
    val path = context.self.path.toPath

    val startTime: Timestamp = Timestamp.create()

    VirtualField
      .find(classOf[PersistentRepr], classOf[PersistenceEventStartTime])
      .set(event, PersistenceEventStartTime(startTime))

    EventBus(context.system)
      .publishEvent(
        PersistingEventStarted(
          path,
          event.persistenceId,
          event.sequenceNr,
          startTime
        )
      )
  }
}
