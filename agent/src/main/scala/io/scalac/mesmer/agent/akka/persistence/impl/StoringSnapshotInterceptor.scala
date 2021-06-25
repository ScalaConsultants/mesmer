package io.scalac.mesmer.agent.akka.persistence.impl

import akka.actor.typed.scaladsl.{ AbstractBehavior, ActorContext }
import akka.persistence.SaveSnapshotSuccess
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.SnapshotCreated
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.{ ReflectionFieldUtils, Timestamp }
import net.bytebuddy.asm.Advice._

object StoringSnapshotInterceptor extends PersistenceUtils {

  private lazy val contextGetter =
    ReflectionFieldUtils.getGetter("akka.actor.typed.scaladsl.AbstractBehavior", "context")

  @OnMethodEnter
  def onSaveSnapshotResponse(
    @Argument(0) response: AnyRef,
    @This self: AbstractBehavior[_]
  ): Unit = {
    val context = contextGetter.invoke(self).asInstanceOf[ActorContext[_]]
    response match {
      case SaveSnapshotSuccess(meta) =>
        context.log.trace("Snapshot for {} created", meta.persistenceId)
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
