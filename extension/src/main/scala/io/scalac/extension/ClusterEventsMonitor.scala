package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{ Cluster, Subscribe }
import io.scalac.core.model._
import io.scalac.extension.ClusterEventsMonitor.Command.MemberEventWrapper
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.metric.ClusterMetricsMonitor.Labels

object ClusterEventsMonitor extends ClusterMonitorActor {

  sealed trait Command

  object Command {
    private[ClusterEventsMonitor] final case class MemberEventWrapper(event: MemberEvent) extends Command
  }

  def apply(clusterMonitor: ClusterMetricsMonitor): Behavior[Command] =
    OnClusterStartUp { selfMember =>
      Behaviors.setup { context =>
        val reachabilityAdapter = context.messageAdapter[MemberEvent](MemberEventWrapper.apply)

        Cluster(context.system).subscriptions ! Subscribe(
          reachabilityAdapter,
          classOf[MemberEvent]
        )

        val boundMonitor = clusterMonitor.bind(Labels(selfMember.uniqueAddress.toNode))

        boundMonitor.nodeDown.incValue(0L)

        Behaviors.receiveMessage { case MemberEventWrapper(event) =>
          event match {
            case MemberDowned(_) => boundMonitor.nodeDown.incValue(1L)
            case _               =>
          }
          Behaviors.same
        }
      }
    }
}
