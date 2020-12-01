package io.scalac.agent

import java.lang.reflect.Method

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryStarted
import net.bytebuddy.asm.Advice

import scala.reflect.runtime.{ universe => ru }

class RecoveryStartedInterceptor

object RecoveryStartedInterceptor {

  private val behaviorSetupField = {
    val mirror = ru.runtimeMirror(Thread.currentThread().getContextClassLoader)
    mirror.staticClass("akka.persistence.typed.internal.ReplayingSnapshot").toType.decl(ru.TermName("setup")).asTerm
  }

  private val persistenceIdField = {
    val mirror = ru.runtimeMirror(Thread.currentThread().getContextClassLoader)
    mirror.staticClass("akka.persistence.typed.internal.BehaviorSetup").toType.decl(ru.TermName("persistenceId")).asTerm
  }

  val reflectivePersistenceIdLens: Any => PersistenceId = ref => {
    val mirror = ru.runtimeMirror(Thread.currentThread().getContextClassLoader)

    val setup = mirror.reflect(ref).reflectField(behaviorSetupField).get
    mirror.reflect(setup).reflectField(persistenceIdField).get.asInstanceOf[PersistenceId]
  }

  @Advice.OnMethodEnter
  def enter(
    @Advice.Origin method: Method,
    @Advice.AllArguments parameters: Array[Object],
    @Advice.This thiz: Object
  ): Unit = {
    System.out.println("Recovery startup intercepted. Method: " + method + ", This: " + thiz)
    val context       = parameters(0).asInstanceOf[ActorContext[_]]
    val persistenceId = reflectivePersistenceIdLens(thiz)
    EventBus(context.system)
      .publishEvent(RecoveryStarted(context.self.path.toString, persistenceId.id, System.currentTimeMillis()))
  }
}
