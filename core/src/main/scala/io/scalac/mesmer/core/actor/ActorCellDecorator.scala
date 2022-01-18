package io.scalac.mesmer.core.actor

import io.scalac.mesmer.core.util.ReflectionFieldUtils

object ActorCellDecorator {

  final val fieldName = "_actorCellMetrics"

  private lazy val (getter, _) = ReflectionFieldUtils.getHandlers("akka.actor.ActorCell", fieldName)

  // TODO this shouldn't fail when agent is not present - None should be returned
  def get(actorCell: Object): Option[ActorCellMetrics] =
    Option(getter.invoke(actorCell)).map(_.asInstanceOf[ActorCellMetrics])

}
