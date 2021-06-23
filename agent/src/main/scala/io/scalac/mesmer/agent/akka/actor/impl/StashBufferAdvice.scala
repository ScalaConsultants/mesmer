package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice

object StashBufferAdvice {

  @Advice.OnMethodExit
  def stash(
    @Advice.FieldValue("akka$actor$typed$internal$StashBufferImpl$$ctx") ctx: ActorContext[_]
  ): Unit =
    ActorCellDecorator.get(ctx.toClassic).foreach(_.stashSize.inc())

}
