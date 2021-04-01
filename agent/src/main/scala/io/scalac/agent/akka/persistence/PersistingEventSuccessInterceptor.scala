package io.scalac.agent.akka.persistence
import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.PersistentRepr
import net.bytebuddy.asm.Advice._

import io.scalac.core.model._
import io.scalac.core.util.Timestamp
import io.scalac.core.event.EventBus
import io.scalac.core.event.PersistenceEvent.PersistingEventFinished
import net.bytebuddy.asm.Advice._
class PersistingEventSuccessInterceptor
object PersistingEventSuccessInterceptor {

  @OnMethodEnter
  def onWriteSuccess(@Argument(0) context: ActorContext[_], @Argument(1) event: PersistentRepr): Unit = {
    val path = context.self.path.toPath

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
