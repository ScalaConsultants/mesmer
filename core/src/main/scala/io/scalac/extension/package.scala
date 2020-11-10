package io.scalac

import java.util.UUID

import akka.actor.typed.receptionist.ServiceKey

package object `extension` {

  lazy val agentListenerActorKey: ServiceKey[AgentListenerActorMessage] =
    ServiceKey[AgentListenerActorMessage](s"io.scalac.extension.agent-listener-actor-${UUID.randomUUID()}")

}
