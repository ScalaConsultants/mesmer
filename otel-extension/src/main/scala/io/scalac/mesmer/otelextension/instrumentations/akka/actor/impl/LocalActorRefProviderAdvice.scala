package io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import net.bytebuddy.asm.Advice.Argument
import net.bytebuddy.asm.Advice.OnMethodExit
import net.bytebuddy.asm.Advice.Return

import io.scalac.mesmer.core.event.ActorEvent.ActorCreated
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.model.ActorRefTags

object LocalActorRefProviderAdvice {

  @OnMethodExit
  def actorOf(@Return ref: ActorRef, @Argument(0) system: ActorSystem): Unit =
    EventBus(system.toTyped)
      .publishEvent(ActorCreated(ActorRefTags(ref, Set.empty)))
}
