package io.scalac.mesmer.core.util

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import akka.MesmerMirrorTypes.Cell
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

  @inline final def cell(actorRef: ActorRef): Cell = underlying(actorRef) // alias
  @inline final def underlying(actorRef: ActorRef): Cell =
    underlyingMethodHandler.invoke(actorRef).asInstanceOf[Cell]

  @inline final def children(actorRef: ActorRef): immutable.Iterable[ActorRef] =
    childrenMethodHandler.invoke(actorRef).asInstanceOf[immutable.Iterable[ActorRef]]

  @inline private final def isWithCell(actorRef: ActorRef): Boolean =
    actorRefWithCellClass.isInstance(actorRef)

  object Local { // optional accessors

    @inline final def cell(actorRef: ActorRef): Option[Cell]       = underlying(actorRef) // alias
    @inline final def underlying(actorRef: ActorRef): Option[Cell] = ifLocal(actorRef)(identity)

    @inline final private def ifLocal[T](actorRef: ActorRef)(value: Cell => T): Option[T] = {
      lazy val cell = ActorRefOps.cell(actorRef)
      Option.when(isWithCell(actorRef) && ActorCellOps.isLocal(cell))(value(cell))
    }

  }

}
