package io.scalac.mesmer.core.actor

import akka.MesmerMirrorTypes.Cell
import akka.actor.ActorContext
import io.opentelemetry.instrumentation.api.util.VirtualField

object ActorCellDecorator {

  @inline def getMetrics(actorCell: ActorContext): Option[ActorCellMetrics] = Option(
    VirtualField.find(classOf[ActorContext], classOf[ActorCellMetrics]).get(actorCell)
  )

  @inline def getMetrics(actorCell: Cell): Option[ActorCellMetrics] =
    getMetrics(actorCell.asInstanceOf[ActorContext])

  @inline def set(actorCell: ActorContext, metrics: ActorCellMetrics): Unit =
    VirtualField.find(classOf[ActorContext], classOf[ActorCellMetrics]).set(actorCell, metrics)
}
