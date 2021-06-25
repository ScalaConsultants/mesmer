package io.scalac.mesmer.agent.akka.persistence.impl

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.{ ReflectionFieldUtils, Timestamp }
import net.bytebuddy.asm.Advice

object RecoveryStartedInterceptor extends PersistenceUtils {
  import AkkaPersistenceAgent.logger

  lazy val persistenceIdHandle = ReflectionFieldUtils.chain(replayingSnapshotsSetupGetter, behaviorSetupPersistenceId)

  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) context: ActorContext[_],
    @Advice.This thiz: AnyRef
  ): Unit = {
    val path = context.self.path.toPath
    logger.trace("Started actor {} recovery", path)

    val persistenceId = persistenceIdHandle.invoke(thiz).asInstanceOf[PersistenceId]

    EventBus(context.system)
      .publishEvent(RecoveryStarted(path, persistenceId.id, Timestamp.create()))
  }
}
