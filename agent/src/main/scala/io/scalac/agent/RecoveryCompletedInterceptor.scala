package io.scalac.agent

import java.lang.reflect.Method

import _root_.akka.actor.typed.scaladsl.ActorContext
import _root_.akka.persistence.typed.PersistenceId
import _root_.akka.util.Timeout
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.PersistenceEvent.RecoveryFinished
import net.bytebuddy.asm.Advice

import scala.concurrent.duration._
import scala.util.Try

case class Settings(role: String)
case class Person(name: String, settings: Settings)

class RecoveryCompletedInterceptor

object RecoveryCompletedInterceptor {

  private val setupField = {
    val setup = Class.forName("akka.persistence.typed.internal.ReplayingEvents").getDeclaredField("setup")
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
    println("Recovery completion intercepted. Method: " + method + ", This: " + thiz)
    val actorContext       = parameters(0).asInstanceOf[ActorContext[_]]
    implicit val ec        = actorContext.system.executionContext
    implicit val scheduler = actorContext.system.scheduler
    implicit val timeout   = Timeout(1.second)

    persistenceIdExtractor(thiz).fold(
      _.printStackTrace(),
      persistenceId =>
        EventBus(actorContext.system)
          .publishEvent(
            RecoveryFinished(actorContext.self.path.toString, persistenceId.id, System.currentTimeMillis())
          )
    )
  }
}
