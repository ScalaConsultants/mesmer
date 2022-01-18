package io.scalac.mesmer.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe

import io.scalac.mesmer.core.model._
import io.scalac.mesmer.extension.ClusterEventsMonitor.Command.MemberEventWrapper
import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor
import io.scalac.mesmer.extension.metric.ClusterMetricsMonitor.Attributes

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

        val boundMonitor = clusterMonitor.bind(Attributes(selfMember.uniqueAddress.toNode))

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
