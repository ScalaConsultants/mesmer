package io.scalac.agent.akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ ActorRef, ActorSystem }
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.EventBus
import io.scalac.core.model.ActorRefDetails
import net.bytebuddy.asm.Advice._

class LocalActorRefProviderAdvice
object LocalActorRefProviderAdvice {

  @OnMethodExit
  def actorOf(@Return ref: ActorRef, @Argument(0) system: ActorSystem): Unit =
    EventBus(system.toTyped)
      .publishEvent(ActorCreated(ActorRefDetails(ref, Set.empty)))
}
