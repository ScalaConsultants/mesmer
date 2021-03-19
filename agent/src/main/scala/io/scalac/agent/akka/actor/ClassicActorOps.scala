package io.scalac.agent.akka.actor

import akka.actor.{ Actor, ActorContext }

object ClassicActorOps {

  @inline final def getContext(actor: Object): ActorContext =
    actor.asInstanceOf[Actor].context

}
