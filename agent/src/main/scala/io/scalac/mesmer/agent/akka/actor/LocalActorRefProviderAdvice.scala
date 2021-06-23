package io.scalac.mesmer.agent.akka.actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.core.event.ActorEvent.ActorCreated
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.model.ActorRefTags

object LocalActorRefProviderAdvice {

  @OnMethodExit
  def actorOf(@Return ref: ActorRef, @Argument(0) system: ActorSystem): Unit =
    EventBus(system.toTyped)
      .publishEvent(ActorCreated(ActorRefTags(ref, Set.empty)))
}
