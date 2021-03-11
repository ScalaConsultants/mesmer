package io.scalac.agent.akka.actor

import akka.actor.Actor

import io.scalac.extension.actor.MessageCounterDecorators

private[actor] final class WrappedReceive(receive: Actor.Receive, actorCell: Object) extends Actor.Receive {

  def isDefinedAt(msg: Any): Boolean = {
    // Disclaimer: Strong Assumption
    // We're rely on `Actor.aroundReceive` implementation which call `PartialFunction.applyOrElse`.
    // i.e., we're rely that `isDefiendAt` is called once per message received.
    MessageCounterDecorators.Received.inc(actorCell)
    receive.isDefinedAt(msg)
  }

  def apply(msg: Any): Unit = try {
    receive.apply(msg)
    MessageCounterDecorators.Processed.inc(actorCell)
  } catch {
    case t: Throwable =>
      MessageCounterDecorators.Failed.inc(actorCell)
      throw t
  }

}
