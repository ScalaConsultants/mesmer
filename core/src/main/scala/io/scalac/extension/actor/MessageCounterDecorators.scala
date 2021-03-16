package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import akka.actor.Actor
import akka.actor.typed.TypedActorContext

import io.scalac.core.util.CounterDecorator

object MessageCounterDecorators {

  final object Received  extends CounterDecorator.FixedClass("receivedMessages", "akka.actor.ActorCell")
  final object Failed    extends CounterDecorator.FixedClass("failedMessages", "akka.actor.ActorCell")
  final object Unhandled extends CounterDecorator.FixedClass("unhandledMessages", "akka.actor.ActorCell")

  final object FailedAtSupervisor {

    private lazy val actorCellGetter = {
      val method = Class
        .forName("akka.actor.ClassicActorContextProvider")
        .getDeclaredMethod("classicActorContext")
      method.setAccessible(true)
      MethodHandles.lookup().unreflect(method)
    }

    def inc(context: TypedActorContext[_]): Unit = {
      val actorCell = actorCellGetter.invoke(context)
      MessageCounterDecorators.Failed.inc(actorCell)
    }

  }

  final object UnhandledAtActor {
    def inc(actor: Object): Unit =
      Unhandled.inc(actor.asInstanceOf[Actor].context)
  }

}
