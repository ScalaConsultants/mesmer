package io.scalac.mesmer.agent.akka.actor

import akka.actor.ActorRef
import io.scalac.mesmer.extension.actor.ActorCellDecorator
import net.bytebuddy.asm.Advice._

class ActorCellReceiveMessageInstrumentation
object ActorCellReceiveMessageInstrumentation {

  @OnMethodEnter
  def onEnter(@This actorCell: Object, @Argument(0) msg: Any, @FieldValue("self") ref: ActorRef): Unit = {
    if (ref.path.toStringWithoutAddress.contains("user")) {
      println(s"Start processing message ${msg} for actor ${ref.path.toStringWithoutAddress}")
    }
    ActorCellDecorator.get(actorCell).foreach { spy =>
      spy.receivedMessages.inc()
      spy.processingTimer.start()
    }

  }

  @OnMethodExit(onThrowable = classOf[Throwable])
  def onExit(@This actorCell: Object, @Thrown exception: Throwable): Unit =
    ActorCellDecorator.get(actorCell).foreach { spy =>
      if (exception != null && !spy.exceptionHandledMarker.checkAndReset()) {
        spy.failedMessages.inc()
      }
      spy.processingTimeAgg.add(spy.processingTimer.interval())
    }

}
