package io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension

import akka.actor.ActorRef

trait ActorLifecycleEvents

object ActorLifecycleEvents {
  final case class ActorCreated(ref: ActorRef)    extends ActorLifecycleEvents
  final case class ActorTerminated(ref: ActorRef) extends ActorLifecycleEvents
}
