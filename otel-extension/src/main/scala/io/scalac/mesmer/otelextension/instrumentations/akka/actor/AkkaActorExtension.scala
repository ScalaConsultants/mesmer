package io.scalac.mesmer.otelextension.instrumentations.akka.actor

import akka.actor.typed.ActorSystem
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object AkkaActorExtension {

  def registerExtension(system: akka.actor.ActorSystem): Unit = {

    def registration(
      system: akka.actor.ActorSystem,
      sleepDuration: FiniteDuration,
      attemptsLeft: Int
    ): AkkaActorExtension =
      if (attemptsLeft <= 0) {
        throw new RuntimeException("Couldn't register the AkkaActorExtension. Terminating.")
      } else {
        try
          system.toTyped.registerExtension(AkkaActorExtensionId)
        catch {
          case _: Throwable =>
            system.log.info(s"Registering extension failed. Trying once again after $sleepDuration.")
            Thread.sleep(sleepDuration.toMillis)
            registration(system, sleepDuration * 2, attemptsLeft - 1)
        }
      }

    new Thread(() => registration(system, 1.second, 5)).start()
  }

}

class AkkaActorExtension(actorSystem: ActorSystem[_]) extends Extension {
  ActorLifecycleMetricsMonitor.subscribeToEventStream(actorSystem)
}

object AkkaActorExtensionId extends ExtensionId[AkkaActorExtension] {
  override def createExtension(system: ActorSystem[_]): AkkaActorExtension = new AkkaActorExtension(system)
}
