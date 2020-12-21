package io.scalac.extension

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ Behavior, PreRestart }
import akka.cluster.ClusterEvent.{
  MemberEvent,
  MemberRemoved,
  MemberUp,
  ReachableMember,
  UnreachableMember,
  ReachabilityEvent => AkkaReachabilityEvent
}
import akka.cluster.sharding.ShardRegion.CurrentShardRegionState
import akka.cluster.sharding.typed.GetShardRegionState
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityTypeKey }
import akka.cluster.sharding.{ ClusterSharding => ClassicClusterSharding }
import akka.cluster.typed.{ Cluster, Subscribe }
import akka.cluster.{ Member, UniqueAddress }
import akka.util.Timeout
import io.scalac.extension.event.ClusterEvent
import io.scalac.extension.event.ClusterEvent.ShardingRegionInstalled
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.model._

import scala.concurrent.duration._
import scala.language.postfixOps
class ClusterSelfNodeEventsActor
object ClusterSelfNodeEventsActor {

  sealed trait Command extends SerializableMessage

  object Command {
    final case class MonitorRegion(region: String) extends Command

    private[ClusterSelfNodeEventsActor] final case class ClusterMemberEvent(event: MemberEvent) extends Command

    private[ClusterSelfNodeEventsActor] final case class ClusterShardingStatsReceived(
      stats: CurrentShardRegionState
    ) extends Command

    private[ClusterSelfNodeEventsActor] final case class GetClusterShardingStatsInternal(
      regions: String
    ) extends Command

    sealed trait ReachabilityEvent extends Command

    private[ClusterSelfNodeEventsActor] case class NodeUnreachable(address: UniqueAddress) extends ReachabilityEvent

    private[ClusterSelfNodeEventsActor] case class NodeReachable(address: UniqueAddress) extends ReachabilityEvent
  }

  def apply(
    clusterMetricsMonitor: ClusterMetricsMonitor,
    selfMember: Member,
    pingOffset: FiniteDuration = 5.seconds
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      import Command._
      implicit val dispatcher       = ctx.system
      implicit val timeout: Timeout = pingOffset
      val selfAddress               = selfMember.uniqueAddress
      val monitor                   = clusterMetricsMonitor.bind(selfAddress.toNode)
      val clusterShardingStatsAdapter =
        ctx.messageAdapter[CurrentShardRegionState](
          ClusterShardingStatsReceived.apply
        )

      val cluster  = Cluster(ctx.system)
      val sharding = ClusterSharding(ctx.system)
      // current implementation of akka cluster works only on adapted actor systems
      val classicSharding = ClassicClusterSharding(ctx.system.classicSystem)

      val memberStateAdapter =
        ctx.messageAdapter[MemberEvent](ClusterMemberEvent.apply)

      cluster.subscriptions ! Subscribe(
        memberStateAdapter,
        classOf[MemberEvent]
      )

      val memberReachabilityAdapter =
        ctx.messageAdapter[AkkaReachabilityEvent] {
          case UnreachableMember(member) => NodeUnreachable(member.uniqueAddress)
          case ReachableMember(member)   => NodeReachable(member.uniqueAddress)
        }

      cluster.subscriptions ! Subscribe(
        memberReachabilityAdapter,
        classOf[AkkaReachabilityEvent]
      )

      Receptionist(ctx.system).ref ! Register(clusterServiceKey, ctx.messageAdapter[ClusterEvent] {
        case ShardingRegionInstalled(region) => MonitorRegion(region)
      })

      def initialized(
        regions: Seq[String],
        unreachableNodes: Set[UniqueAddress]
      ): Behavior[Command] =
        Behaviors.withTimers[Command] { scheduler =>
          Behaviors
            .receiveMessage[Command] {
              case MonitorRegion(region) => {
                ctx.log.info("Start monitoring region {}", region)
                scheduler.startTimerWithFixedDelay(region, GetClusterShardingStatsInternal(region), pingOffset)
                initialized(regions :+ region, unreachableNodes)
              }

              case ClusterMemberEvent(MemberRemoved(member, _)) => {
                if (unreachableNodes.contains(member.uniqueAddress)) {
                  monitor.unreachableNodes.decValue(1L)
                } else {
                  monitor.reachableNodes.decValue(1L)
                }
                initialized(regions, unreachableNodes - member.uniqueAddress)
              }
              case ClusterMemberEvent(event) => {
                event match {
                  case MemberUp(_) => monitor.reachableNodes.incValue(1L)
                  case _           => //
                }
                Behaviors.same
              }
              case ClusterShardingStatsReceived(stats) => {
                val shards = stats.shards.size
                val entities = stats.shards.foldLeft(0) {
                  case (acc, state) => acc + state.entityIds.size
                }

                ctx.log.trace("Recorded amount of entities {}", entities)
                monitor.entityPerRegion.setValue(entities)
                ctx.log.trace("Recorded amount of shards {}", shards)
                monitor.shardPerRegions.setValue(shards)
                Behaviors.same
              }
              case GetClusterShardingStatsInternal(region) => {

                sharding.shardState ! GetShardRegionState(
                  EntityTypeKey[Any](region),
                  clusterShardingStatsAdapter
                )
                val regions = classicSharding.shardTypeNames.size
                monitor.shardRegionsOnNode.setValue(regions)
                Behaviors.same
              }
              case NodeReachable(address) => {
                ctx.log.trace("Node {} become reachable", address)
                monitor.reachableNodes.incValue(1L)
                monitor.unreachableNodes.decValue(1L)
                initialized(regions, unreachableNodes - address)
              }
              case NodeUnreachable(address) => {
                ctx.log.trace("Node {} become unreachable", address)
                monitor.reachableNodes.decValue(1L)
                monitor.unreachableNodes.incValue(1L)
                initialized(regions, unreachableNodes + address)
              }
            }
            .receiveSignal {
              case (_, PreRestart) => {
                ctx.log.info("Saving all monitored regions")
                regions.map(MonitorRegion).foreach(ctx.self.tell)
                Behaviors.same
              }
            }
        }

      val unreachable = cluster.state.unreachable.map(_.uniqueAddress)
      initialized(Seq.empty, unreachable)
    }
}
