package io.scalac.mesmer.agent.akka.dispatcher

import akka.actor.ActorSystem
import akka.dispatch.Dispatcher
import io.opentelemetry.instrumentation.api.field.VirtualField

object DispatcherDecorator {
  @inline def getActorSystem(dispatcher: Dispatcher): ActorSystem =
    VirtualField.find(classOf[Dispatcher], classOf[ActorSystem]).get(dispatcher)

  @inline def setActorSystem(dispatcher: Dispatcher, actorSystem: ActorSystem): Unit =
    VirtualField.find(classOf[Dispatcher], classOf[ActorSystem]).set(dispatcher, actorSystem)

}
