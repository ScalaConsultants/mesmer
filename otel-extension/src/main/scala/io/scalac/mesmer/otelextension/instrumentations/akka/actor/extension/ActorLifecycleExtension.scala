package io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension

import akka.actor
import akka.actor.typed.ActorSystem
import akka.actor.typed.Extension
import akka.actor.typed.ExtensionId
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success

import io.scalac.mesmer.core.util.Retry

object AkkaActorExtension {

  private val log = LoggerFactory.getLogger(classOf[AkkaActorExtension])

  def registerExtension(system: akka.actor.ActorSystem): Unit =
    new Thread(new Runnable() {
      override def run(): Unit =
        Retry.retryWithPauses(10, 2.seconds)(register(system)) match {
          case Failure(error) =>
            log.error(s"Failed to install the Actor Lifecycle Monitoring Extension. Reason: $error")
          case Success(_) => log.info("Successfully installed the Actor Lifecycle Monitoring Extension.")
        }
    }).start()

  private def register(system: actor.ActorSystem) =
    system.toTyped.registerExtension(AkkaActorExtensionId)
}

class AkkaActorExtension(actorSystem: ActorSystem[_]) extends Extension {
  ActorLifecycleMetricsMonitor.subscribeToEventStream(actorSystem)
}

object AkkaActorExtensionId extends ExtensionId[AkkaActorExtension] {
  override def createExtension(system: ActorSystem[_]): AkkaActorExtension = new AkkaActorExtension(system)
}
