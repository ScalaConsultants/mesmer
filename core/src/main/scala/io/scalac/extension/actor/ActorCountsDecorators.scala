package io.scalac.extension.actor

import java.lang.invoke.MethodHandles
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.Actor
import akka.actor.typed.TypedActorContext

import io.scalac.core.util.{ CounterDecorator, ReflectionFieldUtils }

object ActorCountsDecorators {

  final object Received  extends CounterDecorator.FixedClass("akka.actor.ActorCell", "receivedMessages")
  final object Failed    extends CounterDecorator.FixedClass("akka.actor.ActorCell", "failedMessages")
  final object Unhandled extends CounterDecorator.FixedClass("akka.actor.ActorCell", "unhandledMessages")
  final object Sent      extends CounterDecorator.FixedClass("akka.actor.ActorCell", "sentMessages")

  final object FailedAtSupervisor {

    private lazy val actorCellGetter = {
      val method = Class
        .forName("akka.actor.ClassicActorContextProvider")
        .getDeclaredMethod("classicActorContext")
      method.setAccessible(true)
      MethodHandles.lookup().unreflect(method)
    }

    @inline def inc(context: TypedActorContext[_]): Unit = {
      val actorCell = actorCellGetter.invoke(context)
      Failed.inc(actorCell)
      FailHandled.setHandled(actorCell)
    }

  }

  final object FailHandled {
    val fieldName                     = "exceptionHandledMarker"
    private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

    @inline def initialize(actorCell: Object): Unit      = setter.invoke(actorCell, new AtomicBoolean(false))
    @inline def checkAndRest(actorCell: Object): Boolean = get(actorCell).getAndSet(false)

    @inline private[ActorCountsDecorators] def setHandled(actorCell: Object): Unit = get(actorCell).set(true)

    @inline private def get(actorCell: Object): AtomicBoolean = getter.invoke(actorCell).asInstanceOf[AtomicBoolean]
  }

  final object UnhandledAtActor {
    @inline def inc(actor: Object): Unit =
      Unhandled.inc(actor.asInstanceOf[Actor].context)
  }

}
