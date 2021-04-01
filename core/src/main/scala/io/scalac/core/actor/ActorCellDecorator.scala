package io.scalac.core.actor

import io.scalac.core.util.ReflectionFieldUtils

object ActorCellDecorator {

  val fieldName = "_actorCellMetrics"

  private lazy val (getter, setter) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

  def initialize(actorCell: Object): Unit =
    setter.invoke(actorCell, ActorCellMetrics())

  //TODO this shouldn't fail when agent is not present - None should be returned
  def get(actorCell: Object): Option[ActorCellMetrics] =
    Option(getter.invoke(actorCell)).map(_.asInstanceOf[ActorCellMetrics])

}
