package io.scalac.extension.actor

import io.scalac.core.util.ReflectionFieldUtils

object ActorCellDecorator {

  val fieldName = "actorCellSpy"

  private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

  def initialize(actorCell: Object): Unit =
    setter.invoke(actorCell, ActorCellMetrics())

  def get(actorCell: Object): Option[ActorCellMetrics] =
    Option(getter.invoke(actorCell)).map(_.asInstanceOf[ActorCellMetrics])

}
