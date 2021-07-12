package io.scalac.mesmer.agent.akka.actor.impl

import net.bytebuddy.implementation.bind.annotation.FieldValue

import io.scalac.mesmer.core.actor.ActorCellDecorator
import io.scalac.mesmer.core.actor.ActorCellMetrics

final class ActorCellInitializer(init: ActorCellMetrics => Unit) {

  def initField(@FieldValue(ActorCellDecorator.fieldName) metrics: ActorCellMetrics): Unit =
    init(metrics)

}
