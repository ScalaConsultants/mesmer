package io.scalac.agent.akka.actor

import akka.actor.Actor

import io.scalac.extension.actor.ProcessedMessagesHolder

private[actor] final class WrappedReceive(receive: Actor.Receive, actorCell: Object) extends Actor.Receive {

  def isDefinedAt(msg: Any): Boolean = receive.isDefinedAt(msg)

  def apply(msg: Any): Unit = {
    receive.apply(msg)
    ProcessedMessagesHolder.inc(actorCell)
  }

}
