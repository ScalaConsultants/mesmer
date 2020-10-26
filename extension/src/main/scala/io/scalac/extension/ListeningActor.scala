package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Subscribe}

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
