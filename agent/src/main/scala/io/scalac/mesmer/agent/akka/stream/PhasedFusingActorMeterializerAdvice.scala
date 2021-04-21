package io.scalac.mesmer.agent.akka.stream

import akka.AkkaMirrorTypes
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.adapter._
import net.bytebuddy.asm.Advice._

import io.scalac.mesmer.core.event.ActorEvent
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.model.ActorRefDetails
import io.scalac.mesmer.core.model.Tag

class PhasedFusingActorMeterializerAdvice

object PhasedFusingActorMeterializerAdvice {

  @OnMethodExit
  def getPhases(@Return ref: ActorRef, @This self: AkkaMirrorTypes.ExtendedActorMaterializerMirror): Unit =
    EventBus(self.system.toTyped).publishEvent(ActorEvent.TagsSet(ActorRefDetails(ref, Set(Tag.stream))))
}
