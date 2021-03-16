package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import akka.actor.Actor
import akka.actor.typed.TypedActorContext

import io.scalac.core.util.CounterDecorator

object ActorCountsDecorators {

  final object Received  extends CounterDecorator.FixedClass("akka.actor.ActorCell", "receivedMessages")
  final object Failed    extends CounterDecorator.FixedClass("akka.actor.ActorCell", "failedMessages")
  final object Unhandled extends CounterDecorator.FixedClass("akka.actor.ActorCell", "unhandledMessages")

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
      Failed.inc(actorCell)
    }

  }

  final object UnhandledAtActor {
    def inc(actor: Object): Unit =
      Unhandled.inc(actor.asInstanceOf[Actor].context)
  }

}
