package io.scalac.mesmer.agent.akka.persistence.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.api.field.VirtualField
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.agent.akka.persistence.PersistenceEventStartTime
import io.scalac.mesmer.agent.akka.persistence.PersistenceInstruments
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.PersistingEventFinished
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Interval
import io.scalac.mesmer.core.util.Timestamp

object PersistingEventSuccessAdvice {

  @OnMethodEnter
  def onWriteSuccess(@Argument(0) context: ActorContext[_], @Argument(1) event: PersistentRepr): Unit = {
    val path: Path = context.self.path.toPath

    val endTime = Timestamp.create()
    EventBus(context.system)
      .publishEvent(
        PersistingEventFinished(
          path,
          event.persistenceId,
          event.sequenceNr,
          endTime
        )
      )

    val startTime: PersistenceEventStartTime =
      VirtualField.find(classOf[PersistentRepr], classOf[PersistenceEventStartTime]).get(event)

    val eventDuration: Interval = Timestamp.interval(startTime.startTime, endTime)

    // TODO: I'm skipping the "node" attribute here, but afaik it was optional anyway.
    val attributes = Attributes
      .builder()
      .put("path", context.self.path.toPath)
      .put("persistence_id", event.persistenceId)
      .build()
    PersistenceInstruments.persistentEventTotalCounter.add(1, attributes)

    PersistenceInstruments.persistentEventRecorder.record(eventDuration.toMillis, attributes)
  }
}
