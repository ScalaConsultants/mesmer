package io.scalac.mesmer.agent.akka.dispatcher

import akka.actor.ActorSystem
import akka.dispatch.Dispatcher

object DispatcherDecorator {
  private val map = scala.collection.mutable.Map[Dispatcher, ActorSystem]()
  def getActorSystem(dispatcher: Dispatcher): Option[ActorSystem]            = map.get(dispatcher)
  def setActorSystem(dispatcher: Dispatcher, actorSystem: ActorSystem): Unit = map.put(dispatcher, actorSystem)
}
