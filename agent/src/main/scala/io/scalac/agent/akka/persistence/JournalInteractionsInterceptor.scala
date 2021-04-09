package io.scalac.agent.akka.persistence
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import net.bytebuddy.asm.Advice._

import io.scalac.core.event.EventBus
import io.scalac.core.event.PersistenceEvent.PersistingEventStarted
import io.scalac.core.model._
import io.scalac.core.util.Timestamp

class JournalInteractionsInterceptor
object JournalInteractionsInterceptor {

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
