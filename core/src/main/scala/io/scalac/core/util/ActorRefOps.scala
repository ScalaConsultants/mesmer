package io.scalac.core.util

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import akka.actor.ActorRef

import scala.collection.immutable

object ActorRefOps {

  private final val actorRefWithCellClass = Class.forName("akka.actor.ActorRefWithCell")

  private final val (underlyingMethodHandler, childrenMethodHandler) = {
    val lookup = MethodHandles.lookup()
    (
      lookup.findVirtual(actorRefWithCellClass, "underlying", methodType(ActorCellOps.cellClass)),
      lookup.findVirtual(actorRefWithCellClass, "children", methodType(classOf[immutable.Iterable[ActorRef]]))
    )
  }

  @inline final def isLocal(actorRef: ActorRef): Boolean = Local.cell(actorRef).nonEmpty

  @inline final def cell(actorRef: ActorRef): Object = underlying(actorRef) // alias
  @inline final def underlying(actorRef: ActorRef): Object =
    underlyingMethodHandler.invoke(actorRef)

  @inline final def children(actorRef: ActorRef): immutable.Iterable[ActorRef] =
    childrenMethodHandler.invoke(actorRef).asInstanceOf[immutable.Iterable[ActorRef]]

  @inline private final def isWithCell(actorRef: ActorRef): Boolean =
    actorRefWithCellClass.isInstance(actorRef)

  object Local { // optional accessors

    @inline final def cell(actorRef: ActorRef): Option[Object]       = underlying(actorRef) // alias
    @inline final def underlying(actorRef: ActorRef): Option[Object] = ifLocal(actorRef)(identity)

    @inline final private def ifLocal[T](actorRef: ActorRef)(value: Object => T): Option[T] = {
      lazy val cell = ActorRefOps.cell(actorRef)
      Option.when(isWithCell(actorRef) && ActorCellOps.isLocal(cell))(value(cell))
    }

  }

}
