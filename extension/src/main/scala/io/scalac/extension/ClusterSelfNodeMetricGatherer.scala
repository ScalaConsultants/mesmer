package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.{
  CurrentClusterState,
  MemberDowned,
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
import io.scalac.extension.metric.ClusterMetricsMonitor
import io.scalac.extension.metric.ClusterMetricsMonitor.BoundMonitor
import io.scalac.extension.model._

import scala.concurrent.duration._
import scala.language.postfixOps

object ClusterSelfNodeMetricGatherer {

  sealed trait Command extends SerializableMessage

  object Command {
    case class MonitorRegion(region: String) extends SerializableMessage

    private[extension] case class ClusterMemberEvent(event: MemberEvent) extends Command

    private[extension] case class ClusterShardingStatsReceived(
      stats: ClusterShardingStats
    ) extends Command

    private[extension] case class GetClusterShardingStatsInternal(
      regions: String
    ) extends Command

    sealed trait ReachabilityEvent extends Command

    private[extension] case object NodeUnreachable extends ReachabilityEvent

    private[extension] case object NodeReachable extends ReachabilityEvent

    trait InitializationEvent extends Command

    case class Initialized(state: CurrentClusterState) extends InitializationEvent

    case object InitializationTimeout extends InitializationEvent
  }

  def apply(
    clusterMetricsMonitor: ClusterMetricsMonitor,
    initRegions: List[String],
    pingOffset: FiniteDuration = 5.seconds,
    initTimeout: Option[FiniteDuration] = None
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      ctx.log.debug("BOOTING UP")
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

      val initializationAdapter =
        ctx.messageAdapter[SelfUp](up => Initialized(up.currentClusterState))

      cluster.subscriptions ! Subscribe(initializationAdapter, classOf[SelfUp])

      def initialized(monitor: BoundMonitor, selfAddress: UniqueAddress) =
        Behaviors.withTimers[Command] { scheduler =>
          if (initRegions.nonEmpty) {

            initRegions.foreach { region =>
              ctx.log.info("Start monitoring sharding region {}", region)
              scheduler.startTimerWithFixedDelay(
                region,
                GetClusterShardingStatsInternal(region),
                pingOffset
              )
            }
          } else {
            ctx.log.warn("No initial regions specified")
          }

          Behaviors.receiveMessage {

            case ClusterMemberEvent(event) => {
              ctx.log.info(s"${event.toString}")

              event match {
                case MemberUp(_)         => monitor.reachableNodes.incValue(1L)
                case MemberRemoved(_, _) => monitor.reachableNodes.decValue(1L)
                case _                   => // ignore other cases
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
            case reachabilityEvent: ReachabilityEvent =>
              reachabilityEvent match {
                case NodeReachable => {
                  ctx.log.trace("Node become reachable")
                  monitor.reachableNodes.incValue(1L)
                  monitor.unreachableNodes.decValue(1L)
                }
                case NodeUnreachable => {
                  ctx.log.trace("Node become unreachable")
                  monitor.reachableNodes.decValue(1L)
                  monitor.unreachableNodes.incValue(1L)
                }
              }
              Behaviors.same
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
              case Initialized(clusterState) => {
                ctx.log.info("Successful initialization")
                val selfAddress = cluster.selfMember.uniqueAddress
                val boundMonitor =
                  clusterMetricsMonitor.bind(selfAddress.toNode)
                timer.cancel(timeoutKey)
                buffer.unstashAll(initialized(boundMonitor, selfAddress))
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
