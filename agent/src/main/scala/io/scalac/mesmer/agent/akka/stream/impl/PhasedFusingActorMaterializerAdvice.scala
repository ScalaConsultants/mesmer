package io.scalac.mesmer.agent.akka.stream.impl

import akka.AkkaMirrorTypes
import akka.actor.ActorRef
import akka.actor.typed.scaladsl.adapter._
import io.scalac.mesmer.core.event.{ ActorEvent, EventBus }
import io.scalac.mesmer.core.model.{ ActorRefTags, Tag }
import net.bytebuddy.asm.Advice._

object PhasedFusingActorMaterializerAdvice {

  @OnMethodExit
  def actorOf(@Return ref: ActorRef, @This self: Object): Unit =
    EventBus(self.asInstanceOf[AkkaMirrorTypes.ExtendedActorMaterializerMirror].system.toTyped)
      .publishEvent(ActorEvent.TagsSet(ActorRefTags(ref, Set(Tag.stream))))
}
