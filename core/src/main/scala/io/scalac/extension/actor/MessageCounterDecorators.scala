package io.scalac.extension.actor

import java.lang.invoke.MethodHandles

import io.scalac.core.util.CounterDecorator

object MessageCounterDecorators {

  final object Received extends CounterDecorator.FixedClass("receivedMessages", "akka.actor.ActorCell")
  final object Failed   extends CounterDecorator.FixedClass("failedMessages", "akka.actor.ActorCell")

  final object UnhandledAtActor extends CounterDecorator.Registry("unhandledMessages")

  final object UnhandledAtCell extends CounterDecorator.FixedClass("unhandledMessagesBackup", "akka.actor.ActorCell") {
    private lazy val actorGetter = {
      val field  = Class.forName(decoratedClassName).getDeclaredField("_actor")
      val lookup = MethodHandles.publicLookup()
      field.setAccessible(true)
      lookup.unreflectGetter(field)
    }

    @inline override def take(actorCell: Object): Option[Long] = {
      val actor = actorGetter.invoke(actorCell)
      Option(actor)
        .flatMap(UnhandledAtActor.take)
        .orElse(super.take(actorCell))
      // when actor is null (on start or just after terminate), we attempt to take from this counter
    }

  }

}
