package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.UniqueAddress
import akka.cluster.typed.{Cluster, Subscribe}
import io.scalac.extension.model.Event.ClusterChangedEvent
import io.scalac.extension.upstream.EventStream

import scala.util.{Failure, Success}

object ListeningActor {

  sealed trait Command

  object Command {
    final case class ClusterChanged(status: String, node: UniqueAddress) extends Command

    private[extension] final case object PushSuccessful extends Command

    private[extension] final case class PushRejected(code: Int) extends Command

    private[extension] final case class PushFailed(exception: Throwable) extends Command
    private[extension] final case class Init()
  }

  def apply(
    eventStream: EventStream[ClusterChangedEvent]
  )(): Behavior[Command] =
    Behaviors.setup { context =>
      import Command._

      implicit val system = context.system.classicSystem

      val reachabilityAdapter = context.messageAdapter[ReachabilityEvent] {
        case UnreachableMember(member) =>
          ClusterChanged("unreachable", member.uniqueAddress)
        case ReachableMember(member) =>
          ClusterChanged("reachable", member.uniqueAddress)
      }

      Cluster(context.system).subscriptions ! Subscribe(
        reachabilityAdapter,
        classOf[ReachabilityEvent]
      )

      Behaviors.receiveMessage {
        case ClusterChanged(status, node) => {

          context.pipeToSelf(
            eventStream.push(ClusterChangedEvent(status, node))
          ) {
            case Success(_)         => PushSuccessful
            case Failure(exception) => PushFailed(exception)
          }
          Behaviors.same
        }
        case PushSuccessful => {
          context.log.info("Successfully push events")
          Behaviors.same
        }
        case PushRejected(code) => {
          context.log.warn(s"Push requested with code ${code}")
          Behaviors.same
        }
        case PushFailed(ex) => {
          context.log.error("Push failed", ex)
          Behaviors.same
        }
      }
    }
}
