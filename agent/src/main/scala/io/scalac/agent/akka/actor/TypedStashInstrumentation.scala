package io.scalac.agent.akka.actor

import akka.actor.typed.scaladsl.ActorContext

import net.bytebuddy.asm.Advice._

class TypedStashInstrumentation
object TypedStashInstrumentation {

  @OnMethodExit
  def onStashExit(
    @FieldValue("_size") size: Int,
    @FieldValue("akka$actor$typed$internal$StashBufferImpl$$ctx") ctx: ActorContext[_]
  ): Unit =
    StashInstrumentation.publish(size, ctx.self, ctx)

}
