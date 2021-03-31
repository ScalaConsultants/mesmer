package io.scalac.agent.akka.actor

import akka.actor.typed.scaladsl.ActorContext

import net.bytebuddy.asm.Advice._

import io.scalac.core.event.ActorEvent.StashMeasurement

//class TypedStashInstrumentation
//object TypedStashInstrumentation {
//
//  @OnMethodExit
//  def onStashExit(
//    @FieldValue("_size") size: Int,
//    @FieldValue("akka$actor$typed$internal$StashBufferImpl$$ctx") ctx: ActorContext[_],
//    @Argument(0) msg: Any
//  ): Unit =
//    if (msg != null && !msg.isInstanceOf[StashMeasurement]) {
//      StashInstrumentation.publish(size, ctx.self, ctx)
//    }
//
//}
