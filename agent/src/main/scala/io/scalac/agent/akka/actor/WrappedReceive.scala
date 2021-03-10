package io.scalac.agent.akka.actor

import akka.actor.Actor

import io.scalac.core.util.Timestamp
import io.scalac.extension.actor.MessagesCountersHolder

private[actor] final class WrappedReceive(receive: Actor.Receive, actorCell: Object) extends Actor.Receive {

  def isDefinedAt(msg: Any): Boolean = {
    // Disclaimer: Strong Assumption
    // We're rely on `Actor.aroundReceive` implementation which call `PartialFunction.applyOrElse`.
    // i.e., we're rely that `isDefiendAt` is called once per message received.
    MessagesCountersHolder.Received.inc(actorCell)
    receive.isDefinedAt(msg)
  }

  def apply(msg: Any): Unit = {
    val processingStart = Timestamp.create()
    try {
      receive.apply(msg)
      MessagesCountersHolder.Processed.inc(actorCell)
    } catch {
      case t: Throwable =>
        MessagesCountersHolder.Failed.inc(actorCell)
        throw t
    } finally {
      val processingTimeMs = processingStart.interval(Timestamp.create())
      // ???
    }
  }

}
