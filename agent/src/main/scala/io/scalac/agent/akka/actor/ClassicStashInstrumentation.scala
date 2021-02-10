package io.scalac.agent.akka.actor

import akka.actor.typed.scaladsl.adapter._

import net.bytebuddy.asm.Advice._

import io.scalac.extension.event.ActorEvent.StashMeasurement
import io.scalac.extension.event.EventBus

class ClassicStashInstrumentation
object ClassicStashInstrumentation {

  @OnMethodExit
  def onStashExit(@This stashSupport: Any): Unit = {
    val size    = Utils.getStashSize(stashSupport)
    val ref     = Utils.getActorRef(stashSupport)
    val context = Utils.getContext(stashSupport)
    EventBus(context.system.toTyped).publishEvent(StashMeasurement(size, ref.path.toStringWithoutAddress))
  }

}
