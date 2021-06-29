package io.scalac.mesmer.agent.akka.actor.impl

import io.scalac.mesmer.core.actor.{ ActorCellDecorator, ActorCellMetrics }
import net.bytebuddy.implementation.bind.annotation.FieldValue

final class ActorCellInitializer(init: ActorCellMetrics => Unit) {

  def initField(@FieldValue(ActorCellDecorator.fieldName) metrics: ActorCellMetrics): Unit =
    init(metrics)

}
