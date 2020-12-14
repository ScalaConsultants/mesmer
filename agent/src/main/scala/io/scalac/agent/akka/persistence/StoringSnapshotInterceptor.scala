package io.scalac.agent.akka.persistence

import akka.actor.typed.scaladsl.ActorContext
import akka.persistence.SaveSnapshotSuccess
import net.bytebuddy.asm.Advice._
class StoringSnapshotInterceptor
object StoringSnapshotInterceptor {

  @OnMethodEnter
  def onSaveSnapshotResponse(
    @Argument(0) response: AnyRef,
    @FieldValue("context") context: ActorContext[_]
  ): Unit =
    response match {
      case SaveSnapshotSuccess(meta) => {
        context.log.error("Successful snapshot intercepted xoxo")
      }
      case _ =>
    }

}
