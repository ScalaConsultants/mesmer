package io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension

import akka.actor
import akka.actor.ExtendedActorSystem
import akka.actor.typed._
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.cluster.typed.Cluster
import org.slf4j.LoggerFactory

import scala.concurrent.duration.DurationInt
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import io.scalac.mesmer.core.AkkaDispatcher
import io.scalac.mesmer.core.util.Retry

class AkkaClusterMonitorExtension(actorSystem: ActorSystem[_]) extends Extension {

  private val log = LoggerFactory.getLogger(classOf[AkkaClusterMonitorExtension])

  private val name = "mesmerClusterEventsMonitor"

  private val dispatcher: DispatcherSelector = AkkaDispatcher.safeDispatcherSelector(actorSystem)

  checkPreconditions(actorSystem) match {
    case Left(initializationError) => log.error(s"$name Initialization error occurred: $initializationError")
    case Right(_)                  => startActor()
  }

  private def checkPreconditions(actorSystem: ActorSystem[_]): Either[String, Unit] = for {
    _ <- reflectiveIsInstanceOf("akka.actor.typed.internal.adapter.ActorSystemAdapter", actorSystem)
    classic = actorSystem.classicSystem.asInstanceOf[ExtendedActorSystem]
    _ <- reflectiveIsInstanceOf("akka.cluster.ClusterActorRefProvider", classic.provider)
  } yield ()

  private def startActor(): ActorRef[ClusterEventsMonitor.MemberEventWrapper] =
    actorSystem.systemActorOf(
      Behaviors
        .supervise(ClusterEventsMonitor())
        .onFailure[Exception](SupervisorStrategy.restart),
      name,
      dispatcher
    )

  private def reflectiveIsInstanceOf(className: String, ref: Any): Either[String, Unit] =
    Try(Class.forName(className)).toEither.left.map {
      case _: ClassNotFoundException => s"Class $className not found"
      case e                         => e.getMessage
    }.filterOrElse(_.isInstance(ref), s"Ref $ref is not instance of $className").map(_ => ())

}

object AkkaClusterMonitorExtension {

  private val log = LoggerFactory.getLogger(classOf[AkkaClusterMonitorExtension])

  def registerExtension(system: akka.actor.ActorSystem): Unit =
    new Thread(new Runnable() {
      override def run(): Unit = {
        println(s"register extension ${Thread.currentThread().getName}")
        Retry.retryWithPrecondition(10, 2.seconds)(system.toTyped.hasExtension(Cluster))(register(system))
      } match {
        case Failure(error) =>
          log.error(s"Failed to install the Akka Cluster Monitoring Extension. Reason: $error")
        case Success(_) => log.info("Successfully installed the Akka Cluster Monitoring Extension.")
      }
    })
      .start()

  private def register(system: actor.ActorSystem) = system.toTyped.registerExtension(AkkaClusterMonitorExtensionId)
}

object AkkaClusterMonitorExtensionId extends ExtensionId[AkkaClusterMonitorExtension] {
  override def createExtension(system: ActorSystem[_]): AkkaClusterMonitorExtension =
    new AkkaClusterMonitorExtension(system)
}
