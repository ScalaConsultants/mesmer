package io.scalac.mesmer.agent.akka.persistence

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import net.bytebuddy.asm.Advice

import scala.util.Try

import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.PersistenceEvent.RecoveryStarted
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {
  import AkkaPersistenceAgent.logger

  private val setupField = {
    val setup = Class.forName("akka.persistence.typed.internal.ReplayingSnapshot").getDeclaredField("setup")
    setup.setAccessible(true)
    setup
  }
  private val persistenceIdField = {
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

  @Advice.OnMethodEnter
  def enter(
    @Advice.Argument(0) context: ActorContext[_],
    @Advice.This thiz: AnyRef
  ): Unit = {
    val path = context.self.path.toPath
    logger.trace("Started actor {} recovery", path)
    persistenceIdExtractor(thiz).fold(
      _.printStackTrace(),
      persistenceId =>
        EventBus(context.system)
          .publishEvent(RecoveryStarted(path, persistenceId.id, Timestamp.create()))
    )
  }
}
