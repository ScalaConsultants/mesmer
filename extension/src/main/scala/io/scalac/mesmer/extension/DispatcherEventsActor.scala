package io.scalac.mesmer.extension

import akka.actor.typed.Behavior
import io.scalac.mesmer.core.event.DispatcherEvent
import io.scalac.mesmer.core.model.Node
import io.scalac.mesmer.extension.service.PathService

object DispatcherEventsActor {

  sealed trait Event extends SerializableMessage

  object Event {
    private[extension] final case class DispatcherEventWrapper(event: DispatcherEvent) extends Event
  }

  def apply(pathService: PathService, node: Option[Node] = None): Behavior[Event] = ???
}
