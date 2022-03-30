package io.scalac.mesmer.core.util

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType.methodType

import akka.MesmerMirrorTypes.Cell

object ActorCellOps {

  private[util] final val cellClass = Class.forName("akka.actor.Cell")

  private final val (isLocalMethodHandler, numberOfMessagesMethodHandler) = {
    val lookup = MethodHandles.lookup()
    (
      lookup.findVirtual(cellClass, "isLocal", methodType(classOf[Boolean])),
      lookup.findVirtual(cellClass, "numberOfMessages", methodType(classOf[Int]))
    )
  }

  @inline final def isLocal(cell: Cell): Boolean =
    isLocalMethodHandler.invoke(cell).asInstanceOf[Boolean]

  @inline final def numberOfMessages(cell: Cell): Int =
    numberOfMessagesMethodHandler.invoke(cell).asInstanceOf[Int]

}
