package io.scalac

import java.util.UUID

import akka.actor.typed.receptionist.ServiceKey

package object `extension` {

  val persistenceService: ServiceKey[AgentListenerActorMessage] =
    ServiceKey[AgentListenerActorMessage](s"io.scalac.extension.metric-listener-actor")

}
