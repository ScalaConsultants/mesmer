package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.sharding.ShardRegion.ClusterShardingStats
import akka.cluster.sharding.typed.GetClusterShardingStats
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityTypeKey}
import akka.cluster.typed.{Cluster, Subscribe}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps

object LocalSystemListener {

  sealed trait Command extends SerializableMessage

  case class MonitorRegion(region: String) extends SerializableMessage

  private case class ClusterMemberEvent(event: MemberEvent) extends Command

  private case class ClusterShardingStatsReceived(stats: ClusterShardingStats)
      extends Command

  private case class GetClusterShardingStatsInternal(regions: String)
      extends Command

  def apply(initRegions: List[String],
            pingOffset: FiniteDuration = 5.seconds): Behavior[Command] =
    Behaviors.setup(ctx => {
      implicit val dispatcher = ctx.system
      implicit val timeout: Timeout = 5 seconds

      val cluster = Cluster(ctx.system)

      val sharding = ClusterSharding(ctx.system)

      val memberStateAdapter =
        ctx.messageAdapter[MemberEvent](ClusterMemberEvent.apply)

      cluster.subscriptions ! Subscribe(
        memberStateAdapter,
        classOf[MemberEvent]
      )

      val clusterShardingStatsAdapter =
        ctx.messageAdapter[ClusterShardingStats](
          ClusterShardingStatsReceived.apply
        )

      Behaviors.withTimers[Command](scheduler => {

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
            stats.regions.foreach {
              case (address, shardStats) =>
                val entitiesSum = shardStats.stats.values.sum
                ctx.log.info(
                  s"Address: ${address} has: ${shardStats.stats.size} with total of ${entitiesSum}"
                )
            }
            Behaviors.same
          }
          case GetClusterShardingStatsInternal(region) => {
            sharding.shardState ! GetClusterShardingStats(
              EntityTypeKey[Any](region),
              pingOffset,
              clusterShardingStatsAdapter
            )
            Behaviors.same
          }
        }
      })

    })

}
