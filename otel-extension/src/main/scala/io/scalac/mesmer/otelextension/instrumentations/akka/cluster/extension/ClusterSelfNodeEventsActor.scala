package io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.{ ReachabilityEvent => AkkaReachabilityEvent }
import akka.cluster.UniqueAddress
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import org.slf4j.LoggerFactory

import io.scalac.mesmer.core.model.AkkaNodeOps
import io.scalac.mesmer.otelextension.instrumentations.akka.common.SerializableMessage

object ClusterSelfNodeEventsActor extends ClusterMonitorActor {

  private val log = LoggerFactory.getLogger(ClusterSelfNodeEventsActor.getClass)

  private val meter: Meter = GlobalOpenTelemetry.getMeter("mesmer")

  private val reachableNodesCounter = meter
    .upDownCounterBuilder("mesmer_akka_cluster_reachable_nodes")
    .setDescription("Amount of reachable nodes.")
    .build()

  private val unreachableNodesCounter = meter
    .upDownCounterBuilder("mesmer_akka_cluster_unreachable_nodes")
    .setDescription("Amount of unreachable nodes.")
    .build()

  sealed trait Command extends SerializableMessage

  object Command {
    sealed trait ReachabilityEvent extends Command

    final case class ClusterMemberEvent(event: MemberEvent) extends Command

    case class NodeUnreachable(address: UniqueAddress) extends ReachabilityEvent

    case class NodeReachable(address: UniqueAddress) extends ReachabilityEvent
  }

  override def apply(): Behavior[Command] =
    OnClusterStartup { selfMember =>
      Behaviors.setup { ctx =>
        import ctx.{ messageAdapter, system }
        import Command._

        val attributes: Attributes = Attributes.builder().put("node", selfMember.uniqueAddress.toNode).build()

        val cluster = Cluster(system)

        cluster.subscriptions ! Subscribe(
          messageAdapter[MemberEvent](ClusterMemberEvent.apply),
          classOf[MemberEvent]
        )

        cluster.subscriptions ! Subscribe(
          messageAdapter[AkkaReachabilityEvent] {
            case UnreachableMember(member) => NodeUnreachable(member.uniqueAddress)
            case ReachableMember(member)   => NodeReachable(member.uniqueAddress)
          },
          classOf[AkkaReachabilityEvent]
        )

        def behavior(unreachableNodes: Set[UniqueAddress]): Behavior[Command] = Behaviors.receiveMessage[Command] {
          case ClusterMemberEvent(MemberRemoved(member, _)) =>
            if (unreachableNodes.contains(member.uniqueAddress)) {
              unreachableNodesCounter.add(-1, attributes)
            } else {
              reachableNodesCounter.add(-1)
            }
            behavior(unreachableNodes - member.uniqueAddress)

          case ClusterMemberEvent(event) =>
            event match {
              case MemberUp(_) => reachableNodesCounter.add(1L)
              case _           => // do nothing
            }
            Behaviors.same

          case NodeReachable(address) =>
            log.trace("Node {} become reachable", address)

            reachableNodesCounter.add(1)
            unreachableNodesCounter.add(-1)
            behavior(unreachableNodes - address)

          case NodeUnreachable(address) =>
            log.trace("Node {} become unreachable", address)
            unreachableNodesCounter.add(1)
            reachableNodesCounter.add(-1)
            behavior(unreachableNodes + address)

        }

        val unreachable = cluster.state.unreachable.map(_.uniqueAddress)
        behavior(unreachable)
      }
    }
}
