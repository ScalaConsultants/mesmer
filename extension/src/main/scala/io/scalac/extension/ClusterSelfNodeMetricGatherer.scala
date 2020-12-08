package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.Receptionist.Register
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.{
  CurrentClusterState,
  MemberEvent,
  MemberRemoved,
  MemberUp,
  ReachableMember,
  UnreachableMember,
  ReachabilityEvent => AkkaReachabilityEvent
}
import akka.cluster.UniqueAddress
import akka.cluster.sharding.ShardRegion.ClusterShardingStats
import akka.cluster.sharding.typed.GetClusterShardingStats
import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityTypeKey }
import akka.cluster.sharding.{ ClusterSharding => ClassicClusterSharding }
import akka.cluster.typed.{ Cluster, SelfUp, Subscribe }
import akka.util.Timeout
import io.scalac.extension.event.ClusterEvent
import io.scalac.extension.event.ClusterEvent.ShardingRegionInstalled
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.metric.ClusterMetricsMonitor.BoundMonitor
import io.scalac.extension.model._

import scala.concurrent.duration._
import scala.language.postfixOps

object ClusterSelfNodeMetricGatherer {

  sealed trait Command extends SerializableMessage

  object Command {
    final case class MonitorRegion(region: String) extends Command

    private[extension] final case class ClusterMemberEvent(event: MemberEvent) extends Command

    private[extension] final case class ClusterShardingStatsReceived(
      stats: ClusterShardingStats
    ) extends Command

    private[extension] final case class GetClusterShardingStatsInternal(
      regions: String
    ) extends Command

    sealed trait ReachabilityEvent extends Command

    private[extension] case class NodeUnreachable(address: UniqueAddress) extends ReachabilityEvent

    private[extension] case class NodeReachable(address: UniqueAddress) extends ReachabilityEvent

    trait InitializationEvent extends Command

    final case class Initialized(state: CurrentClusterState) extends InitializationEvent

    case object InitializationTimeout extends InitializationEvent
  }

  def apply(
    clusterMetricsMonitor: ClusterMetricsMonitor,
    pingOffset: FiniteDuration = 5.seconds,
    initTimeout: Option[FiniteDuration] = None
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      import Command._
      implicit val dispatcher       = ctx.system
      implicit val timeout: Timeout = pingOffset

      val cluster = Cluster(ctx.system)

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

      val clusterShardingStatsAdapter =
        ctx.messageAdapter[ClusterShardingStats](
          ClusterShardingStatsReceived.apply
        )

      cluster.subscriptions ! Subscribe(
        memberReachabilityAdapter,
        classOf[AkkaReachabilityEvent]
      )

      val initializationAdapter =
        ctx.messageAdapter[SelfUp](up => Initialized(up.currentClusterState))

      cluster.subscriptions ! Subscribe(initializationAdapter, classOf[SelfUp])

      Receptionist(ctx.system).ref ! Register(clusterServiceKey, ctx.messageAdapter[ClusterEvent] {
        case ShardingRegionInstalled(region) => MonitorRegion(region)
      })

      def initialized(
        regions: Seq[String],
        monitor: BoundMonitor,
        selfAddress: UniqueAddress,
        unreachableNodes: Set[UniqueAddress]
      ): Behavior[Command] =
        Behaviors.withTimers[Command] { scheduler =>
          Behaviors.receiveMessage {
            case MonitorRegion(region) => {
              ctx.log.info("Start monitoring region {}", region)
              scheduler.startTimerWithFixedDelay(region, GetClusterShardingStatsInternal(region), pingOffset)
              initialized(regions :+ region, monitor, selfAddress, unreachableNodes)
            }

            case ClusterMemberEvent(MemberRemoved(member, _)) => {
              if (unreachableNodes.contains(member.uniqueAddress)) {
                monitor.unreachableNodes.decValue(1L)
              } else {
                monitor.reachableNodes.decValue(1L)
              }
              initialized(regions, monitor, selfAddress, unreachableNodes - member.uniqueAddress)
            }
            case ClusterMemberEvent(event) => {
              event match {
                case MemberUp(_) => monitor.reachableNodes.incValue(1L)
                case _           => //
              }
              Behaviors.same
            }
            case ClusterShardingStatsReceived(stats) => {
              stats.regions
                .get(selfAddress.address)
                .fold {
                  ctx.log.warn(
                    s"No information on shards for node ${selfAddress.address}"
                  )
                } { shardsStats =>
                  val entities = shardsStats.stats.values.sum
                  val shards   = shardsStats.stats.size

                  ctx.log.trace("Recorded amount of entities {}", entities)
                  monitor.entityPerRegion.setValue(entities)
                  ctx.log.trace("Recorded amount of shards {}", shards)
                  monitor.shardPerRegions.setValue(shards)

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
              monitor.shardRegionsOnNode.setValue(regions)
              Behaviors.same
            }
            case NodeReachable(address) => {
              ctx.log.trace("Node {} become reachable", address)
              monitor.reachableNodes.incValue(1L)
              monitor.unreachableNodes.decValue(1L)
              initialized(regions, monitor, selfAddress, unreachableNodes - address)
            }
            case NodeUnreachable(address) => {
              ctx.log.trace("Node {} become unreachable", address)
              monitor.reachableNodes.decValue(1L)
              monitor.unreachableNodes.incValue(1L)
              initialized(regions, monitor, selfAddress, unreachableNodes + address)
            }
            case InitializationTimeout => Behaviors.ignore
          }
        }

      def initialize(): Behavior[Command] =
        Behaviors.withTimers[Command] { timer =>
          val timeoutKey: Any = InitializationTimeout
          initTimeout.foreach(duration =>
            timer
              .startSingleTimer(timeoutKey, InitializationTimeout, duration)
          )

          Behaviors.withStash(1024) { buffer =>
            Behaviors.receiveMessage {
              case Initialized(_) => {
                ctx.log.info("Successful initialization")
                val selfAddress = cluster.selfMember.uniqueAddress
                val boundMonitor =
                  clusterMetricsMonitor.bind(selfAddress.toNode)
                timer.cancel(timeoutKey)
                buffer.unstashAll(initialized(Seq.empty, boundMonitor, selfAddress, Set.empty))
              }
              case InitializationTimeout => {
                ctx.log.error("Initialization timeout")
                Behaviors.stopped
              }
              case other => {
                buffer.stash(other)
                Behaviors.same
              }
            }
          }
        }
      initialize()
    }
}
