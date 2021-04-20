package io.scalac.mesmer.agent.akka.actor
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import net.bytebuddy.asm.Advice

import io.scalac.mesmer.extension.actor.ActorCellDecorator

class StashBufferAdvice
object StashBufferAdvice {

  @Advice.OnMethodExit
  def stash(
    @Advice.FieldValue("akka$actor$typed$internal$StashBufferImpl$$ctx") ctx: ActorContext[_]
  ): Unit =
    ActorCellDecorator.get(ctx.toClassic).foreach(_.stashSize.inc())

}
