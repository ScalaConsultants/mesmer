package io.scalac.agent.akka.stream

import akka.ExtendedActorMaterializerOps
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.adapter._
import io.scalac.core.Tag
import io.scalac.extension.event.{ EventBus, TagEvent }
import net.bytebuddy.asm.Advice._
class PhasedFusingActorMeterializerAdvice
object PhasedFusingActorMeterializerAdvice {

  @OnMethodExit
  def getPhases(@Return ref: ActorRef, @This self: ExtendedActorMaterializerOps.Type): Unit =
    EventBus(self.system.toTyped).publishEvent(TagEvent(ref, Tag.stream))
}
