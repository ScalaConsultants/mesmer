package io.scalac.agent.akka.actor

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.{ actor => classic }

import io.scalac.core.util.ActorPathOps
import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus

object StashInstrumentation {

  @inline private[actor] def publish(size: Int, ref: classic.ActorRef, context: classic.ActorContext): Unit =
    EventBus(context.system.toTyped).publishEvent(StashMeasurement(size, ActorPathOps.getPathString(ref)))

  @inline private[actor] def publish(size: Int, ref: ActorRef[_], context: ActorContext[_]): Unit = {
    val pubRef = EventBus(context.system).refFor[StashMeasurement]
    if (pubRef != ref) pubRef ! StashMeasurement(size, ActorPathOps.getPathString(ref))
  }

}
