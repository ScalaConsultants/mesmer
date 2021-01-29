package io.scalac.extension

import java.util.concurrent.ForkJoinPool

import scala.concurrent.duration._
import scala.concurrent._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

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
import akka.cluster.sharding.ShardRegion.{ CurrentShardRegionState, GetShardRegionStats, ShardRegionStats }
import akka.cluster.sharding.typed.GetShardRegionState
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityTypeKey }
import akka.cluster.sharding.{ ClusterSharding => ClassicClusterSharding }
import akka.cluster.typed.{ Cluster, Subscribe }
import akka.pattern.ask
import akka.util.Timeout

import org.slf4j.LoggerFactory

import io.scalac.extension.event.ClusterEvent
import io.scalac.extension.event.ClusterEvent.ShardingRegionInstalled
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.model._

class ClusterSelfNodeEventsActor
object ClusterSelfNodeEventsActor {

  sealed trait Command extends SerializableMessage

  object Command {
    final case class MonitorRegion(region: String) extends Command

    private[ClusterSelfNodeEventsActor] final case class ClusterMemberEvent(event: MemberEvent) extends Command

    private[ClusterSelfNodeEventsActor] final case class InternalGetShardRegionStats(
      region: String
    ) extends Command

    private[ClusterSelfNodeEventsActor] final case class ShardRegionStatsReceived(
      stats: CurrentShardRegionState
    ) extends Command

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
        import ctx.{ log, messageAdapter, system }

        import Command._

        val monitor         = clusterMetricsMonitor.bind(selfMember.uniqueAddress.toNode)
        val cluster         = Cluster(system)
        val sharding        = ClusterSharding(system)
        val classicSharding = ClassicClusterSharding(system.classicSystem)
        // classicSharding disclaimer: current implementation of akka cluster works only on adapted actor systems
        val logger = LoggerFactory.getLogger(classOf[ClusterSelfNodeEventsActor])

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

        // adapters

        val regionStateAdapter = messageAdapter[CurrentShardRegionState](ShardRegionStatsReceived.apply)

        // async observables

        def queryRegionStats(andThen: Set[ShardRegionStats] => _): Unit = {
          implicit val t: Timeout           = Timeout(2 seconds)
          implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool)
          Future
            .sequence(
              classicSharding.shardTypeNames
                .map(region => classicSharding.shardRegion(region) ? GetShardRegionStats)
                .map(_.mapTo[ShardRegionStats])
            )
            .onComplete {
              case Success(regionStats) =>
                if (regionStats.forall(_.failed.isEmpty)) {
                  andThen(regionStats)
                } else {
                  val regionsFailed = regionStats.filter(_.failed.nonEmpty)
                  val failedMap     = regionsFailed.flatMap(r => r.failed)
                  logger.warn(
                    "{} regions failed. Shards failed: {}",
                    regionsFailed.size,
                    failedMap.mkString("(", ",", ")")
                  )
                }
              case Failure(ex) =>
                logger.warn("Failed to query region stats", ex)
            }
        }

        // async metrics

        monitor.entitiesOnNode.setUpdater { result =>
          queryRegionStats { regionsStats =>
            val entities = regionsStats.foldLeft(0)(_ + _.stats.values.sum)
            result.observe(entities)
            logger.trace("Recorded amount of entities on node {}", entities)
          }
        }

        monitor.shardRegionsOnNode.setUpdater { result =>
          val regions = classicSharding.shardTypeNames.size
          result.observe(regions)
          logger.trace("Recorded amount of regions on node {}", regions)
        }

        // behavior setup

        def initialized(
          regions: Seq[String],
          unreachableNodes: Set[UniqueAddress]
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
                  initialized(regions, unreachableNodes - member.uniqueAddress)

                case ClusterMemberEvent(event) =>
                  event match {
                    case MemberUp(_) => monitor.reachableNodes.incValue(1L)
                    case _           => //
                  }
                  Behaviors.same

                case NodeReachable(address) =>
                  log.trace("Node {} become reachable", address)
                  monitor.atomically(monitor.reachableNodes, monitor.unreachableNodes)(1L, -1L)
                  initialized(regions, unreachableNodes - address)

                case NodeUnreachable(address) =>
                  log.trace("Node {} become unreachable", address)
                  monitor.atomically(monitor.reachableNodes, monitor.unreachableNodes)(-1L, 1L)
                  initialized(regions, unreachableNodes + address)

                case MonitorRegion(region) =>
                  log.info("Start monitoring region {}", region)
                  scheduler.startTimerWithFixedDelay(region, InternalGetShardRegionStats(region), pingOffset)
                  initialized(regions :+ region, unreachableNodes)

                case InternalGetShardRegionStats(region) =>
                  sharding.shardState ! GetShardRegionState(EntityTypeKey[Any](region), regionStateAdapter)
                  Behaviors.same

                case ShardRegionStatsReceived(regionStats) =>
                  val shards   = regionStats.shards.size
                  val entities = regionStats.shards.foldLeft(0L)(_ + _.entityIds.size)
                  log.trace("Recorded amount of entities {}", entities)
                  monitor.entityPerRegion.setValue(entities)
                  log.trace("Recorded amount of shards {}", shards)
                  monitor.shardPerRegions.setValue(shards)
                  initialized(regions, unreachableNodes)

              }
              .receiveSignal {
                case (_, PreRestart) =>
                  log.info("Saving all monitored regions")
                  regions.map(MonitorRegion).foreach(ctx.self.tell)
                  Behaviors.same
              }
          }

        val unreachable = cluster.state.unreachable.map(_.uniqueAddress)
        initialized(Seq.empty, unreachable)

      }
    }
  }
}
