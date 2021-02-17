package io.scalac.agent.akka.actor

import akka.actor.typed.scaladsl.adapter._
import akka.actor.{ ActorContext, ActorRef }

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus

trait StashInstrumentation {

  protected val extractor: StashInstrumentation.Extractor

  @OnMethodExit
  def onStashExit(@This stash: Any): Unit = {
    import extractor._
    publish(getStashSize(stash), getActorRef(stash), getContext(stash))
  }

  @inline protected def publish(size: Int, ref: ActorRef, context: ActorContext): Unit =
    EventBus(context.system.toTyped).publishEvent(StashMeasurement(size, ref.path.toStringWithoutAddress))

}

object StashInstrumentation {
  trait Extractor {
    def getStashSize(stash: Any): Int
    def getActorRef(stash: Any): ActorRef
    def getContext(stash: Any): ActorContext
  }
}
