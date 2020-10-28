package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.receptionist.ReceptionistSetup
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.actor.typed.scaladsl.adapter._
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.sharding.ShardRegion.ClusterShardingStats
import akka.cluster.typed.Cluster
import akka.pattern.ask
import akka.util.Timeout
import akka.cluster.typed.Subscribe

import scala.util.{Failure, Success}
import scala.language.postfixOps
import scala.concurrent.duration._

object LocalSystemListener {

  sealed trait Command extends SerializableMessage

  case class MonitorRegion(region: String) extends SerializableMessage

  private case class ReceivedStats(stats: ClusterShardingStats) extends Command

  private case class ClusterMemberEvent(event: MemberEvent) extends  Command

  def apply(initRegions: List[String]): Behavior[Command] = Behaviors.setup(ctx => {
    implicit val dispatcher = ctx.system
    implicit val timeout: Timeout = 5 seconds

    val cluster = Cluster(ctx.system)

    val memberStateAdapter = ctx.messageAdapter[MemberEvent](ClusterMemberEvent.apply)

    cluster.subscriptions ! Subscribe(memberStateAdapter, classOf[MemberEvent])

    Behaviors.receiveMessage {
      case ClusterMemberEvent(event) => {
//        val member = event.member
        ctx.log.info(s"${event.toString}")
        Behaviors.same
      }
    }
  })

}
