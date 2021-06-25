package io.scalac.mesmer.agent.akka.persistence.impl

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.{ ReflectionFieldUtils, Timestamp }
import net.bytebuddy.asm.Advice

object RecoveryCompletedInterceptor extends PersistenceUtils {


  private lazy val persistenceIdHandle =
    ReflectionFieldUtils.chain(replayingEventsSetupGetter, behaviorSetupPersistenceId)

  import AkkaPersistenceAgent.logger
  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) actorContext: ActorContext[_],
    @Advice.This thiz: AnyRef
  ): Unit = {
    val path = actorContext.self.path.toPath
    logger.trace("Recovery completed for {}", path)
    val persistenceId = persistenceIdHandle.invoke(thiz).asInstanceOf[PersistenceId]

    EventBus(actorContext.system)
      .publishEvent(
        RecoveryFinished(path, persistenceId.id, Timestamp.create())
      )

  }
}
