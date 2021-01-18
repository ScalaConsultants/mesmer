package io.scalac.extension

import akka.actor.ExtendedActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, DispatcherSelector }
import io.scalac.core.model.ActorNode
import io.scalac.core.util.reflect.AkkaMirrors.{ ActorSystemAdapter, Mirror }
import io.scalac.core.util.{ ActorDiscovery, Timestamp }
import io.scalac.extension.metric.ActorSystemMonitor
import io.scalac.extension.metric.ActorSystemMonitor.Labels
import io.scalac.extension.model.Node

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future }
import scala.util._

object ActorSystemMonitorActor {

  sealed trait Command

  object Command {

    private[extension] case object ScanTree extends Command

    private[extension] case class ScanFinished(state: Try[Seq[ActorNode]]) extends Command

  }

  def apply(monitor: ActorSystemMonitor, ping: FiniteDuration, node: Option[Node] = None): Behavior[Command] =
    Behaviors.setup[Command] { ctx =>
      import ctx._

      require(
        Mirror[ActorSystemAdapter].isInstance(system),
        "only adapted actor systems can be used for monitoring itself"
      )

      implicit val extendedActorSystem = system.classicSystem.asInstanceOf[ExtendedActorSystem]

      val boundMonitor = monitor.bind(Labels(system.name, node))

      Behaviors.withTimers { timer =>
        import Command._
        timer.startTimerWithFixedDelay(ScanTree, ping)

        def capturedTree(state: Seq[ActorNode], scanning: Option[Timestamp]): Behavior[Command] =
          Behaviors.receiveMessage {
            case ScanTree =>
              if (scanning.isDefined) {
                log.warn("Received scan message but previous scan is in progress")
                Behaviors.same
              } else {
                log.debug("Scanning actor tree")
                implicit val executionContext: ExecutionContext = system.dispatchers
                  .lookup(DispatcherSelector.blocking())
                ctx.pipeToSelf(Future(ActorDiscovery.getActorsFrom(extendedActorSystem.provider.rootGuardian)))(ScanFinished)
                capturedTree(state, Some(Timestamp.create()))
              }
            case ScanFinished(Success(result)) => {
              log.debug("Received actor system structure information")
              scanning.foreach { started =>
                val duration = started.interval(Timestamp.create())
                boundMonitor.actorTreeScanDuration.setValue(duration)
              }
              boundMonitor.actors.setValue(result.size)

              capturedTree(result, None)
            }
            // TODO create retry with backoff mechanism
            case ScanFinished(Failure(ex)) => {
              log.error("Actor System scan failed", ex)
              capturedTree(state, None)
            }
          }

        capturedTree(Seq.empty, None)
      }
    }
}
