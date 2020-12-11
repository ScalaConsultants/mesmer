package io.scalac.agent.akka.persistence
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.PersistingEventFinished
import net.bytebuddy.asm.Advice._

class PersistingEventSuccessInterceptor
object PersistingEventSuccessInterceptor {

  @OnMethodEnter
  def onWriteSuccess(@Argument(0) context: ActorContext[_], @Argument(1) event: PersistentRepr): Unit =
    EventBus(context.system)
      .publishEvent(
        PersistingEventFinished(
          event.persistenceId,
          event.sequenceNr,
          System.currentTimeMillis()
        )
      )
}
