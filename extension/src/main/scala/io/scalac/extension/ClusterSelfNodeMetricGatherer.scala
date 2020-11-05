package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.{
  CurrentClusterState,
  MemberEvent,
  ReachableMember,
  UnreachableMember,
  ReachabilityEvent => AkkaReachabilityEvent
}
import akka.cluster.sharding.ShardRegion.ClusterShardingStats
import akka.cluster.sharding.typed.GetClusterShardingStats
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.cluster.sharding.{ClusterSharding => ClassicClusterSharding}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.util.Timeout
import io.scalac.extension.model._
import io.scalac.extension.upstream.ClusterMetricsMonitor

import scala.concurrent.duration._
import scala.language.postfixOps

object ClusterSelfNodeMetricGatherer {

  sealed trait Command extends SerializableMessage

  object Command {
    case class MonitorRegion(region: String) extends SerializableMessage

    private[extension] case class ClusterMemberEvent(event: MemberEvent)
        extends Command

    private[extension] case class ClusterShardingStatsReceived(
      stats: ClusterShardingStats
    ) extends Command

    private[extension] case class GetClusterShardingStatsInternal(
      regions: String
    ) extends Command

    sealed trait ReachabilityEvent extends Command

    private[extension] case object NodeUnreachable extends ReachabilityEvent

    private[extension] case object NodeReachable extends ReachabilityEvent
  }

  def apply(clusterMetricsMonitor: ClusterMetricsMonitor,
            initRegions: List[String],
            pingOffset: FiniteDuration = 5.seconds,
            delayedInit: Option[FiniteDuration] = None): Behavior[Command] =
    Behaviors.setup(ctx => {
      import Command._
      implicit val dispatcher = ctx.system
      implicit val timeout: Timeout = pingOffset

      val cluster = Cluster(ctx.system)

      val sharding = ClusterSharding(ctx.system)

      // current implementation of akka cluster works only on adapted actor systems
      val classicSharding = ClassicClusterSharding(ctx.system.classicSystem)

      val selfAddress = cluster.selfMember.uniqueAddress
      val boundMonitor = clusterMetricsMonitor.bind(selfAddress.toNode)

      val memberStateAdapter =
        ctx.messageAdapter[MemberEvent](ClusterMemberEvent.apply)

      cluster.subscriptions ! Subscribe(
        memberStateAdapter,
        classOf[MemberEvent]
      )

      val memberReachabilityAdapter =
        ctx.messageAdapter[AkkaReachabilityEvent] {
          case UnreachableMember(_) => NodeUnreachable
          case ReachableMember(_)   => NodeReachable
        }

      val clusterShardingStatsAdapter =
        ctx.messageAdapter[ClusterShardingStats](
          ClusterShardingStatsReceived.apply
        )

      cluster.subscriptions ! Subscribe(
        memberReachabilityAdapter,
        classOf[AkkaReachabilityEvent]
      )

      def initializeClusterMetrics(state: CurrentClusterState): Unit = {
        val unreachableMembers = state.unreachable.size
        val allMembers = state.members.size
        boundMonitor.reachableNodes.incValue(allMembers - unreachableMembers)
        boundMonitor.unreachableNodes.incValue(unreachableMembers)
      }

      val initialized = Behaviors.withTimers[Command](scheduler => {

        if (initRegions.nonEmpty) {

          initRegions.foreach(region => {
            ctx.log.info("Start monitoring sharding region {}", region)
            scheduler.startTimerWithFixedDelay(
              region,
              GetClusterShardingStatsInternal(region),
              pingOffset
            )
          })
        } else {
          ctx.log.warn("No initial regions specified")
        }

        Behaviors.receiveMessage {

          case ClusterMemberEvent(event) => {
            ctx.log.info(s"${event.toString}")
            Behaviors.same
          }
          case ClusterShardingStatsReceived(stats) => {
            stats.regions
              .find {
                case (address, _) => address == selfAddress.address
              }
              .fold {
                ctx.log.warn(
                  s"No information on shards for node ${selfAddress.address}"
                )
              } {
                case (_, shardsStats) => {
                  val entities = shardsStats.stats.values.sum
                  val shards = shardsStats.stats.size

                  ctx.log.trace("Recorded amount of entitites {}", entities)
                  boundMonitor.entityPerRegion.setValue(entities)
                  ctx.log.trace("Recorded amount of shards {}", shards)
                  boundMonitor.shardPerRegions.setValue(shards)
                }

              }
            Behaviors.same
          }
          case GetClusterShardingStatsInternal(region) => {

            sharding.shardState ! GetClusterShardingStats(
              EntityTypeKey[Any](region),
              pingOffset,
              clusterShardingStatsAdapter
            )
            val regions = classicSharding.shardTypeNames.size
            boundMonitor.shardRegionsOnNode.setValue(regions)
            Behaviors.same
          }
          case reachabilityEvent: ReachabilityEvent =>
            reachabilityEvent match {
              case NodeReachable => {
                ctx.log.trace("Node become reachable")
                boundMonitor.reachableNodes.incValue(1L)
                boundMonitor.unreachableNodes.decValue(1L)
              }
              case NodeUnreachable => {
                ctx.log.trace("Node become unreachable")
                boundMonitor.reachableNodes.decValue(1L)
                boundMonitor.unreachableNodes.incValue(1L)
              }
            }
            Behaviors.same
        }
      })

      initializeClusterMetrics(cluster.state)
      initialized
    })
}
