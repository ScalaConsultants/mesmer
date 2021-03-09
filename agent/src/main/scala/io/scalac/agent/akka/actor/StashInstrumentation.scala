package io.scalac.agent.akka.actor

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.{ actor => classic }
import io.scalac.core.model._
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus

object StashInstrumentation {

  @inline private[actor] def publish(size: Int, ref: classic.ActorRef, context: classic.ActorContext): Unit =
    publish(size, ref.path.toPath, context.system.toTyped)

  @inline private[actor] def publish(size: Int, ref: ActorRef[_], context: ActorContext[_]): Unit =
    publish(size, ref.path.toPath, context.system)

  @inline private def publish(size: Int, path: Path, system: ActorSystem[_]): Unit =
    EventBus(system).publishEvent(StashMeasurement(size, path))

}
