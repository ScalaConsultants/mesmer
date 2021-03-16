package io.scalac.agent.akka.persistence
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import io.scalac.core.model._
import io.scalac.core.tagging._
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.PersistingEventStarted
import net.bytebuddy.asm.Advice._

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
