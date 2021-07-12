package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.ActorRef
import akka.dispatch.Envelope

object EnvelopeOps {

  @inline final def getSender(envelope: Object): ActorRef =
    envelope.asInstanceOf[Envelope].sender

}
