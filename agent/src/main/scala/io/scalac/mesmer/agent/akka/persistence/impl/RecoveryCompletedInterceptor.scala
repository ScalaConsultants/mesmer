package io.scalac.mesmer.agent.akka.persistence.impl

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.mesmer.agent.akka.persistence.AkkaPersistenceAgent
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryFinished
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp
import net.bytebuddy.asm.Advice

import scala.util.Try

object RecoveryCompletedInterceptor {

  private lazy val setupField = {
    val setup = Class.forName("akka.persistence.typed.internal.ReplayingEvents").getDeclaredField("setup")
    setup.setAccessible(true)
    setup
  }

  private lazy val persistenceIdField = {
    val persistenceId = Class.forName("akka.persistence.typed.internal.BehaviorSetup").getDeclaredField("persistenceId")
    persistenceId.setAccessible(true)
    persistenceId
  }

  val persistenceIdExtractor: Any => Try[PersistenceId] = ref => {
    for {
      setup         <- Try(setupField.get(ref))
      persistenceId <- Try(persistenceIdField.get(setup))
    } yield persistenceId.asInstanceOf[PersistenceId]
  }
  import AkkaPersistenceAgent.logger
  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) actorContext: ActorContext[_],
    @Advice.This thiz: AnyRef
  ): Unit = {
    val path = actorContext.self.path.toPath
    logger.trace("Recovery completed for {}", path)

    persistenceIdExtractor(thiz).fold(
      _.printStackTrace(),
      persistenceId =>
        EventBus(actorContext.system)
          .publishEvent(
            RecoveryFinished(path, persistenceId.id, Timestamp.create())
          )
    )
  }
}
