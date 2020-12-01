package io.scalac.agent

import java.lang.reflect.Method

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import _root_.akka.util.Timeout
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryFinished
import net.bytebuddy.asm.Advice

import scala.concurrent.duration._
import scala.reflect.runtime.{ universe => ru }

case class Settings(role: String)
case class Person(name: String, settings: Settings)

class RecoveryCompletedInterceptor

object RecoveryCompletedInterceptor {

  private val behaviorSetupField = {
    val mirror = ru.runtimeMirror(Thread.currentThread().getContextClassLoader)
    mirror.staticClass("akka.persistence.typed.internal.ReplayingEvents").toType.decl(ru.TermName("setup")).asTerm
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
    println("Recovery completion intercepted. Method: " + method + ", This: " + thiz)
    val actorContext       = parameters(0).asInstanceOf[ActorContext[_]]
    implicit val ec        = actorContext.system.executionContext
    implicit val scheduler = actorContext.system.scheduler
    implicit val timeout   = Timeout(1.second)

    val persistenceId = reflectivePersistenceIdLens(thiz)

    EventBus(actorContext.system)
      .publishEvent(
        RecoveryFinished(actorContext.self.path.toString, persistenceId.id, System.currentTimeMillis())
      )
  }
}
