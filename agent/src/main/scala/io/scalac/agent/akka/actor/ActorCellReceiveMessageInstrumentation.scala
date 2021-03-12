package io.scalac.agent.akka.actor

import java.lang.reflect.Method

import scala.concurrent.duration._

import net.bytebuddy.implementation.bind.annotation.{ SuperMethod, This }

import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.{ ActorCountsDecorators, ActorTimesDecorators }

class ActorCellReceiveMessageInstrumentation
object ActorCellReceiveMessageInstrumentation {

  def receiveMessage(msg: Any, @SuperMethod method: Method, @This actorCell: Object): Unit = {
    ActorCountsDecorators.Received.inc(actorCell)
    val start = Timestamp.create()
    try method.invoke(actorCell, msg)
    catch {
      case t: Throwable =>
        ActorCountsDecorators.Failed.inc(actorCell)
        throw t
    } finally ActorTimesDecorators.ProcessingTime.addTime(actorCell, Timestamp.create().interval(start).milliseconds)
  }

}
