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
import akka.cluster.UniqueAddress
import akka.cluster.sharding.ShardRegion.CurrentShardRegionState
import akka.cluster.sharding.typed.GetShardRegionState
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityTypeKey }
import akka.cluster.sharding.{ ClusterSharding => ClassicClusterSharding }
import akka.cluster.typed.{ Cluster, Subscribe }
import akka.util.Timeout

import io.scalac.extension.event.ClusterEvent
import io.scalac.extension.event.ClusterEvent.ShardingRegionInstalled
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.model._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

class ClusterSelfNodeEventsActor
object ClusterSelfNodeEventsActor {

  sealed trait Command extends SerializableMessage

  object Command {
    final case class MonitorRegion(region: String) extends Command

    private[ClusterSelfNodeEventsActor] final case class ClusterMemberEvent(event: MemberEvent) extends Command

    private[ClusterSelfNodeEventsActor] final case class GetShardRegionStats(
      region: String
    ) extends Command

    private[ClusterSelfNodeEventsActor] final case class ShardRegionStatsReceived(
      stats: Option[CurrentShardRegionState],
      region: String
    ) extends Command

    private[ClusterSelfNodeEventsActor] case object UpdateNodeStats extends Command

    sealed trait ReachabilityEvent extends Command

    private[ClusterSelfNodeEventsActor] case class NodeUnreachable(address: UniqueAddress) extends ReachabilityEvent

    private[ClusterSelfNodeEventsActor] case class NodeReachable(address: UniqueAddress) extends ReachabilityEvent
  }

  def apply(
    clusterMetricsMonitor: ClusterMetricsMonitor,
    pingOffset: FiniteDuration = 5.seconds
  ): Behavior[Command] = {
    OnClusterStartUp { selfMember =>
      Behaviors.setup { ctx =>
        import Command._
        import ctx.{ log, messageAdapter, system }

        val monitor         = clusterMetricsMonitor.bind(selfMember.uniqueAddress.toNode)
        val cluster         = Cluster(system)
        val sharding        = ClusterSharding(system)
        val classicSharding = ClassicClusterSharding(system.classicSystem)
        // classicSharding disclaimer: current implementation of akka cluster works only on adapted actor systems

        // bootstrap messages

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

        Receptionist(system).ref ! Register(
          clusterServiceKey,
          messageAdapter[ClusterEvent] {
            case ShardingRegionInstalled(region) => MonitorRegion(region)
          }
        )

        // behavior setup

        def initialized(
          regions: Seq[String],
          unreachableNodes: Set[UniqueAddress],
          entitiesCount: Map[String, Long] // region -> count
        ): Behavior[Command] =
          Behaviors.withTimers[Command] { scheduler =>
            Behaviors
              .receiveMessage[Command] {

                case ClusterMemberEvent(MemberRemoved(member, _)) =>
                  if (unreachableNodes.contains(member.uniqueAddress)) {
                    monitor.unreachableNodes.decValue(1L)
                  } else {
                    monitor.reachableNodes.decValue(1L)
                  }
                  initialized(regions, unreachableNodes - member.uniqueAddress, entitiesCount)

                case ClusterMemberEvent(event) =>
                  event match {
                    case MemberUp(_) => monitor.reachableNodes.incValue(1L)
                    case _           => //
                  }
                  Behaviors.same

                case NodeReachable(address) =>
                  log.trace("Node {} become reachable", address)
                  monitor.atomically(monitor.reachableNodes, monitor.unreachableNodes)(1L, -1L)
                  initialized(regions, unreachableNodes - address, entitiesCount)

                case NodeUnreachable(address) =>
                  log.trace("Node {} become unreachable", address)
                  monitor.atomically(monitor.reachableNodes, monitor.unreachableNodes)(-1L, 1L)
                  initialized(regions, unreachableNodes + address, entitiesCount)

                case UpdateNodeStats =>
                  val regions = classicSharding.shardTypeNames.size
                  monitor.shardRegionsOnNode.setValue(regions)
                  log.trace("Recorded amount of regions on node {}", regions)

                  val entities = entitiesCount.view.values.sum
                  monitor.entitiesOnNode.setValue(entities)
                  log.trace("Recorded amount of entities on node {}", entities)

                  Behaviors.same

                case MonitorRegion(region) =>
                  log.info("Start monitoring region {}", region)
                  scheduler.startTimerWithFixedDelay(region, GetShardRegionStats(region), pingOffset)
                  scheduler.startTimerWithFixedDelay(selfMember, UpdateNodeStats, pingOffset)
                  initialized(regions :+ region, unreachableNodes, entitiesCount)

                case GetShardRegionStats(region) =>
                  implicit val timeout: Timeout = pingOffset
                  ctx.ask[GetShardRegionState, CurrentShardRegionState](
                    sharding.shardState,
                    replyTo =>
                      GetShardRegionState(
                        EntityTypeKey[Any](region),
                        replyTo
                    )
                  ) {
                    case Success(stats) =>
                      ShardRegionStatsReceived(Some(stats), region)
                    case Failure(exception) =>
                      log.warn(s"Failure to get stats for region $region.", exception)
                      ShardRegionStatsReceived(None, region)
                  }

                  Behaviors.same

                case ShardRegionStatsReceived(opRegionStats, region) =>
                  opRegionStats.fold(
                    Behaviors.same[Command]
                  ) { regionStats =>
                    val shards   = regionStats.shards.size
                    val entities = regionStats.shards.foldLeft(0L)(_ + _.entityIds.size)
                    log.trace("Recorded amount of entities {}", entities)
                    monitor.entityPerRegion.setValue(entities)
                    log.trace("Recorded amount of shards {}", shards)
                    monitor.shardPerRegions.setValue(shards)
                    initialized(regions, unreachableNodes, entitiesCount.updated(region, entities))
                  }
              }
              .receiveSignal {
                case (_, PreRestart) =>
                  log.info("Saving all monitored regions")
                  regions.map(MonitorRegion).foreach(ctx.self.tell)
                  Behaviors.same
              }
          }

        val unreachable = cluster.state.unreachable.map(_.uniqueAddress)
        initialized(Seq.empty, unreachable, Map.empty)

      }
    }
  }
}
