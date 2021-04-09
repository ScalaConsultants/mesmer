package io.scalac.agent.akka.actor

import akka.actor.ActorRef
import akka.dispatch.Envelope

object EnvelopeOps {

  @inline final def getSender(envelope: Object): ActorRef =
    envelope.asInstanceOf[Envelope].sender

}
