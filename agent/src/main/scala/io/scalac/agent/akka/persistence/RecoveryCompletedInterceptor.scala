package io.scalac.agent.akka.persistence

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import _root_.akka.util.Timeout
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryFinished
import net.bytebuddy.asm.Advice

import scala.concurrent.duration._
import scala.util.Try

class RecoveryCompletedInterceptor

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
    val path = actorContext.self.path
    logger.trace("Recovery completed for {}", path)

    persistenceIdExtractor(thiz).fold(
      _.printStackTrace(),
      persistenceId =>
        EventBus(actorContext.system)
          .publishEvent(
            RecoveryFinished(path.toString, persistenceId.id, System.currentTimeMillis())
          )
    )
  }
}
