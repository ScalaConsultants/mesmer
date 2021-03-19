package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import akka.actor.Actor
import akka.actor.typed.TypedActorContext

object ActorCountsDecorators {

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
      ActorCellSpy.get(actorCell).foreach { spy =>
        spy.failedMessages.inc()
        spy.exceptionHandledMarker.mark()
      }
    }

  }

  final object UnhandledAtActor {
    @inline def inc(actor: Object): Unit =
      ActorCellSpy.get(actor.asInstanceOf[Actor].context).foreach(_.unhandledMessages.inc())
  }

}
