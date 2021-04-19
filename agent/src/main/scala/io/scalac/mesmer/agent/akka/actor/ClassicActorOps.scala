package io.scalac.mesmer.agent.akka.actor

import akka.actor.Actor
import akka.actor.ActorContext

object ClassicActorOps {

  @inline final def getContext(actor: Object): ActorContext =
    actor.asInstanceOf[Actor].context

}
