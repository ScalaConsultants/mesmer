package io.scalac.extension.service

import java.lang.invoke.MethodHandles

import akka.{ actor => classic }

import scala.annotation.tailrec
import scala.collection.immutable

import io.scalac.core.util.ActorRefOps

trait ActorTreeTraverser {
  def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef]
  def getRootGuardian(system: classic.ActorSystem): classic.ActorRef

  final def getActorTree(treeRoot: classic.ActorRef): List[classic.ActorRef] = {

    @tailrec
    def loop(unresolved: List[classic.ActorRef], result: List[classic.ActorRef]): List[classic.ActorRef] =
      unresolved match {
        case Nil          => result
        case head :: tail => loop(tail.prependedAll(getChildren(head)), head :: result)
      }
    loop(List(treeRoot), Nil)
  }

  final def getActorTreeFromRootGuardian(system: classic.ActorSystem): List[classic.ActorRef] = getActorTree(
    getRootGuardian(system)
  )
}

object ReflectiveActorTreeTraverser extends ActorTreeTraverser {

  import java.lang.invoke.MethodType.methodType

  private val actorRefProviderClass = classOf[classic.ActorRefProvider]

  private val (providerMethodHandler, rootGuardianMethodHandler) = {
    val lookup = MethodHandles.lookup()
    (
      lookup.findVirtual(classOf[classic.ActorSystem], "provider", methodType(actorRefProviderClass)),
      lookup.findVirtual(
        actorRefProviderClass,
        "rootGuardian",
        methodType(Class.forName("akka.actor.InternalActorRef"))
      )
    )
  }

  def getChildren(actor: classic.ActorRef): immutable.Iterable[classic.ActorRef] =
    if (ActorRefOps.isLocal(actor)) {
      ActorRefOps.children(actor)
    } else {
      immutable.Iterable.empty
    }

  def getRootGuardian(system: classic.ActorSystem): classic.ActorRef = {
    val provider = providerMethodHandler.invoke(system)
    rootGuardianMethodHandler.invoke(provider).asInstanceOf[classic.ActorRef]
  }
}
