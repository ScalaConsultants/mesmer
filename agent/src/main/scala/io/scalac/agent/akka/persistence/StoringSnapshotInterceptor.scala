package io.scalac.agent.akka.persistence

import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext }
import akka.persistence.SaveSnapshotSuccess
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.SnapshotCreated
import net.bytebuddy.asm.Advice._

import java.lang.invoke.{ MethodHandles, MethodType }
class StoringSnapshotInterceptor
object StoringSnapshotInterceptor {

  private val handle = {

    val abstractBehaviorClass = Class
      .forName("akka.actor.typed.scaladsl.AbstractBehavior")
    import MethodHandles._
    import MethodType._

    lookup().findVirtual(abstractBehaviorClass, "context", methodType(classOf[ActorContext[_]]))
  }

  def getContext(ref: AbstractBehavior[_]): ActorContext[_] = handle.invoke(ref).asInstanceOf[ActorContext[_]]

  @OnMethodEnter
  def onSaveSnapshotResponse(
    @Argument(0) response: AnyRef,
    @This self: AbstractBehavior[_]
  ): Unit =
    response match {
      case SaveSnapshotSuccess(meta) => {
        val context = getContext(self)
        context.log.trace("Snapshot for {} created", meta.persistenceId)
        EventBus(context.system)
          .publishEvent(
            SnapshotCreated(
              context.self.path.toString,
              meta.persistenceId,
              meta.sequenceNr,
              Timestamp.create()
            )
          )
      }
      case _ =>
    }

}
