package io.scalac.`extension`

import akka.actor.ActorPath

sealed trait AgentListenerActorMessage

object AgentListenerActorMessage {

  final case class PersistentActorRecoveryDuration(actorPath: ActorPath, durationMs: Long)
      extends AgentListenerActorMessage

}
