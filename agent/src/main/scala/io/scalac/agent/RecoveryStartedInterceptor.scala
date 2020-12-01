package io.scalac.agent

import java.lang.reflect.Method

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted
import net.bytebuddy.asm.Advice

import scala.util.Try

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {

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
    @Advice.Origin method: Method,
    @Advice.AllArguments parameters: Array[Object],
    @Advice.This thiz: Object
  ): Unit = {
    System.out.println("Recovery startup intercepted. Method: " + method + ", This: " + thiz)
    val context = parameters(0).asInstanceOf[ActorContext[_]]
    persistenceIdExtractor(thiz).fold(
      _.printStackTrace(),
      persistenceId =>
        EventBus(context.system)
          .publishEvent(RecoveryStarted(context.self.path.toString, persistenceId.id, System.currentTimeMillis()))
    )
  }
}
