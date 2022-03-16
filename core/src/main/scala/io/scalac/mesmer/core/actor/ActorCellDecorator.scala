package io.scalac.mesmer.core.actor

import akka.MesmerMirrorTypes.Cell
import akka.actor.ActorContext
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.instrumentation.api.field.VirtualField

object ActorCellDecorator {

  @inline def getMetrics(actorCell: ActorContext): Option[ActorCellMetrics] = Option(
    VirtualField.find(classOf[ActorContext], classOf[ActorCellMetrics]).get(actorCell)
  )

  @inline def getMetrics(actorCell: Cell): Option[ActorCellMetrics] =
    getMetrics(actorCell.asInstanceOf[ActorContext])

  @inline def set(actorCell: ActorContext, metrics: ActorCellMetrics): Unit =
    VirtualField.find(classOf[ActorContext], classOf[ActorCellMetrics]).set(actorCell, metrics)

  @inline def setAttributes(actorCell: ActorContext, attributes: Attributes): Unit =
    VirtualField.find(classOf[ActorContext], classOf[Attributes]).set(actorCell, attributes)

  @inline def getCellAttributes(actorCell: ActorContext): Attributes =
    VirtualField.find(classOf[ActorContext], classOf[Attributes]).get(actorCell)

  @inline def getCellAttributes(actorCell: Cell): Attributes =
    getCellAttributes(actorCell.asInstanceOf[ActorContext])
}
