package io.scalac.agent.akka.actor

import akka.actor.{ ActorContext, ActorRef }
import akka.actor.typed.scaladsl.adapter._

import net.bytebuddy.asm.Advice.{ OnMethodExit, This }

import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus

trait StashInstrumentation {

  protected def utils: StashInstrumentation.Utils

  @OnMethodExit
  def onStashExit(@This stash: Any): Unit = {
    val size    = utils.getStashSize(stash)
    val ref     = utils.getActorRef(stash)
    val context = utils.getContext(stash)
    publish(size, ref, context)
  }

  protected def publish(size: Int, ref: ActorRef, context: ActorContext): Unit =
    EventBus(context.system.toTyped).publishEvent(StashMeasurement(size, ref.path.toStringWithoutAddress))

}

object StashInstrumentation {
  trait Utils {
    def getStashSize(stash: Any): Int
    def getActorRef(stash: Any): ActorRef
    def getContext(stash: Any): ActorContext
  }
}