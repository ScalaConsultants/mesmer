package io.scalac.agent.akka.actor

import akka.actor.typed.{ ActorRef, ActorSystem }
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.{ actor => classic }

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus
import StashInstrumentation._

object StashInstrumentation {

  @inline private[actor] def publish(size: Int, ref: classic.ActorRef, context: classic.ActorContext): Unit =
    publish(size, ref.path.toStringWithoutAddress, context.system.toTyped)

  @inline private[actor] def publish(size: Int, ref: ActorRef[_], context: ActorContext[_]): Unit =
    publish(size, ref.path.toStringWithoutAddress, context.system)

  @inline private def publish(size: Int, path: String, system: ActorSystem[_]): Unit =
    EventBus(system).publishEvent(StashMeasurement(size, path))

}
