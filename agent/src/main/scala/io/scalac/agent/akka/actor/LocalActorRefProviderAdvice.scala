package io.scalac.agent.akka.actor
import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ ActorRef, ActorSystem }
import io.scalac.core.event.ActorEvent.ActorCreated
import io.scalac.core.event.EventBus
import net.bytebuddy.asm.Advice._

class LocalActorRefProviderAdvice
object LocalActorRefProviderAdvice {

  @OnMethodExit
  def actorOf(@Return ref: ActorRef, @Argument(0) system: ActorSystem): Unit = {
    println(s"Create ref ${ref}")
    EventBus(system.toTyped).publishEvent(ActorCreated(ref))
  }
}
