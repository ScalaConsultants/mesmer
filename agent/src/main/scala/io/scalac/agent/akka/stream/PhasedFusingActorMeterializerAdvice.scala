package io.scalac.agent.akka.stream

import akka.AkkaMirrorTypes
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.adapter._
import io.scalac.core.event.{ ActorEvent, EventBus }
import io.scalac.core.model.{ ActorRefDetails, Tag }
import net.bytebuddy.asm.Advice._
class PhasedFusingActorMeterializerAdvice

object PhasedFusingActorMeterializerAdvice {

  @OnMethodExit
  def getPhases(@Return ref: ActorRef, @This self: AkkaMirrorTypes.ExtendedActorMaterializerMirror): Unit =
    EventBus(self.system.toTyped).publishEvent(ActorEvent.SetTags(ActorRefDetails(ref, Set(Tag.stream))))
}
