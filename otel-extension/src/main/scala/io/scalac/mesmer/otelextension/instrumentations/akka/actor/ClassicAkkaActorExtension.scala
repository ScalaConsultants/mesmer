package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import akka.actor.ActorSystem
import akka.actor.ExtendedActorSystem
import akka.actor.Extension
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider

class ClassicAkkaActorExtension(actorSystem: ActorSystem) extends Extension {

  def start(): Unit = {
    println("STARTING CLASSIC EXTENSION")
    ActorSystemMetricsActor.subscribeToEventStream(actorSystem)
  }

  start()
}

object ClassicAkkaActorExtensionId extends ExtensionId[ClassicAkkaActorExtension] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem): ClassicAkkaActorExtension = new ClassicAkkaActorExtension(
    system
  )

  override def lookup: ExtensionId[_ <: Extension] = ClassicAkkaActorExtensionId
}

object ClassicAkkaActorExtension {

  def registerExtension(system: ActorSystem): Unit = {
    println("registering extension")
    system.registerExtension(ClassicAkkaActorExtensionId)
    println("registered extension")
  }
}
