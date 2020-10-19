package io.scalac.extension

import io.scalac.serialization.SerializableMessage
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.cluster.typed.Cluster
import akka.cluster.typed.Subscribe
import akka.cluster.ClusterEvent.ClusterDomainEvent
import akka.cluster.ClusterEvent.MemberJoined
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.ClusterEvent.MemberDowned
import akka.cluster.ClusterEvent.MemberLeft
import akka.cluster.ClusterEvent.MemberExited
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberRemoved
import akka.cluster.ClusterEvent.MemberWeaklyUp

object ListeningActor {

  trait Command

  case class ClusterChanged(message: String) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    val clusterAdapter = context.messageAdapter[MemberEvent]({
      case MemberJoined(member)                  => ClusterChanged(s"Joined: ${member.uniqueAddress}")
      case MemberUp(member)                      => ClusterChanged(s"Up: ${member.uniqueAddress}")
      case MemberDowned(member)                  => ClusterChanged(s"Down: ${member.uniqueAddress}")
      case MemberLeft(member)                    => ClusterChanged(s"Left: ${member.uniqueAddress}")
      case MemberExited(member)                  => ClusterChanged(s"Exit: ${member.uniqueAddress}")
      case MemberRemoved(member, previousStatus) => ClusterChanged(s"Removed: ${member.uniqueAddress}")
      case MemberWeaklyUp(member)                => ClusterChanged(s"WeaklyUp: ${member.uniqueAddress}")
      case memberEvent                           => ClusterChanged(s"Other member cluster event ${memberEvent.member.uniqueAddress}")
    })

    Cluster(context.system).subscriptions ! Subscribe(clusterAdapter, classOf[MemberEvent])

    Behaviors.receiveMessage {
      case ClusterChanged(message) => {
        context.log.error(message)
        Behaviors.same
      }
    }
  }
}
