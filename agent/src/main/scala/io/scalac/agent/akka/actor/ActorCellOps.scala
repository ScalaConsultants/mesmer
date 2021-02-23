package io.scalac.agent.akka.actor

import java.lang.invoke.{ MethodHandles, MethodType }

import akka.actor.{ ActorRef, ActorSystem }

object ActorCellOps {

  private val lookup = MethodHandles.publicLookup()

  private val actorCellClass = Class.forName("akka.actor.ActorCell")

  private val systemGetterHandler = {
    val field = actorCellClass.getDeclaredField("system")
    field.setAccessible(true)
    lookup.unreflectGetter(field)
  }

  private val selfGetterHandler = {
    lookup.findVirtual(actorCellClass, "self", MethodType.methodType(classOf[ActorRef]))
  }

  def getSystem(actorCell: Object): ActorSystem = systemGetterHandler.invoke(actorCell)
  def getSelf(actorCell: Object): ActorRef      = selfGetterHandler.invoke(actorCell)

}
