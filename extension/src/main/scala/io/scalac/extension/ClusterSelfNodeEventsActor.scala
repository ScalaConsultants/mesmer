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
import akka.cluster.sharding.{ ShardRegion, ClusterSharding => ClassicClusterSharding }
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

    sealed trait ReachabilityEvent extends Command

    private[ClusterSelfNodeEventsActor] case class NodeUnreachable(address: UniqueAddress) extends ReachabilityEvent

    private[ClusterSelfNodeEventsActor] case class NodeReachable(address: UniqueAddress) extends ReachabilityEvent
  }

  def apply(clusterMetricsMonitor: ClusterMetricsMonitor): Behavior[Command] = {
    OnClusterStartUp { selfMember =>
      Behaviors.setup { ctx =>
        import ctx.{ log, messageAdapter, system }

        import Command._

        val monitor         = clusterMetricsMonitor.bind(selfMember.uniqueAddress.toNode)
        val cluster         = Cluster(system)
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

        // async observables
        {
          implicit val t: Timeout           = Timeout(2 seconds)
          implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(new ForkJoinPool)
          val logger                        = LoggerFactory.getLogger(classOf[ClusterSelfNodeEventsActor])

          type RegionStats    = Map[ShardRegion.ShardId, Int]
          type RegionStatsMap = Map[String, RegionStats]

          def queryAllRegionsStats(andThen: RegionStatsMap => _): Unit = {
            val regions = classicSharding.shardTypeNames.toSeq
            Future
              .sequence(
                regions.map(queryOneRegionStats)
              )
              .onComplete {
                case Success(regionStats) =>
                  andThen(regions.zip(regionStats).toMap)
                case Failure(ex) =>
                  logger.warn("Failed to query region stats", ex)
              }
          }

          def queryOneRegionStats(region: String): Future[RegionStats] =
            (classicSharding.shardRegion(region) ? GetShardRegionStats)
              .mapTo[ShardRegionStats]
              .flatMap { regionStats =>
                if (regionStats.failed.isEmpty) {
                  Future.successful(regionStats.stats)
                } else {
                  val shardsFailed = regionStats.failed
                  val msg          = s"region $region failed. Shards failed: ${shardsFailed.mkString("(", ",", ")")}"
                  Future.failed(new RuntimeException(msg))
                }
              }

          // async metrics

          monitor.entitiesOnNode.setUpdater { result =>
            queryAllRegionsStats { regionsStats =>
              val entities = regionsStats.view.values.flatMap(_.values).sum
              result.observe(entities)
              logger.trace("Recorded amount of entities on node {}", entities)
            }
          }

          monitor.shardRegionsOnNode.setUpdater { result =>
            val regions = classicSharding.shardTypeNames.size
            result.observe(regions)
            logger.trace("Recorded amount of regions on node {}", regions)
          }

          classicSharding.shardTypeNames.foreach { region =>
            monitor
              .entityPerRegion(region)
              .setUpdater(result =>
                queryOneRegionStats(region).foreach { regionStats =>
                  val entities = regionStats.values.sum
                  result.observe(entities)
                  logger.trace("Recorded amount of entities per region {}", entities)
                }
              )

            monitor
              .shardPerRegions(region)
              .setUpdater(result =>
                queryOneRegionStats(region).foreach { regionStats =>
                  val shards = regionStats.size
                  result.observe(shards)
                  logger.trace("Recorded amount of shards per region {}", shards)
                }
              )
          }
        } // end async observables

        // behavior setup

        def initialized(
          regions: Seq[String],
          unreachableNodes: Set[UniqueAddress]
        ): Behavior[Command] =
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
                initialized(regions :+ region, unreachableNodes)

            }
            .receiveSignal {
              case (_, PreRestart) =>
                log.info("Saving all monitored regions")
                regions.map(MonitorRegion).foreach(ctx.self.tell)
                Behaviors.same
            }

        val unreachable = cluster.state.unreachable.map(_.uniqueAddress)
        initialized(Seq.empty, unreachable)

      }
    }
  }
}
