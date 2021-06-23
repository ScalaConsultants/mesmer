package io.scalac.mesmer.agent.akka.stream.impl

import akka.AkkaMirrorTypes
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.adapter._
import io.scalac.mesmer.core.event.{ActorEvent, EventBus}
import io.scalac.mesmer.core.model.{ActorRefTags, Tag}
import net.bytebuddy.asm.Advice._

object PhasedFusingActorMaterializerAdvice {

  @OnMethodExit
  def getPhases(@Return ref: ActorRef, @This self: AkkaMirrorTypes.ExtendedActorMaterializerMirror): Unit =
    EventBus(self.system.toTyped).publishEvent(ActorEvent.TagsSet(ActorRefTags(ref, Set(Tag.stream))))
}
