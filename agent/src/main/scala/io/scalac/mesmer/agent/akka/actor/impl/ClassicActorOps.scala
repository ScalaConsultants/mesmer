package io.scalac.mesmer.agent.akka.actor.impl

import akka.actor.{ Actor, ActorContext }

object ClassicActorOps {

  @inline final def getContext(actor: Object): ActorContext =
    actor.asInstanceOf[Actor].context

}
