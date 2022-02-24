package io.scalac.mesmer.core.actor

import io.opentelemetry.instrumentation.api.field.VirtualField

object ActorCellDecorator {

  @inline def getMetrics(actorCell: Object): Option[ActorCellMetrics] = Option(
    VirtualField.find(classOf[Object], classOf[ActorCellMetrics]).get(actorCell)
  )

  @inline def set(actorCell: Object, metrics: ActorCellMetrics): Unit =
    VirtualField.find(classOf[Object], classOf[ActorCellMetrics]).set(actorCell, metrics)

}
