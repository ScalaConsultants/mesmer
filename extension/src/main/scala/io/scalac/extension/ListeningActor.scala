package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.RequestEntity
import akka.http.scaladsl.model.headers.HttpEncodings.gzip
import akka.http.scaladsl.model.headers.{`Content-Encoding`, RawHeader}
import akka.http.scaladsl.coding.Gzip
import akka.util.ByteString
import scala.util.{Success, Failure}

object ListeningActor {

  trait Command

  final case class ClusterChanged(status: String, node: String) extends Command

  private final case object PushSuccessful extends Command

  private final case class PushRejected(code: Int) extends Command

  private final case class PushFailed(exception: Throwable) extends Command

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    implicit val system = context.system.classicSystem
    val config = context.system.settings.config.getConfig("newrelic")

    val apiKey = config.getString("api_key")
    val accountId = config.getString("account_id")
    val vendorUrl: Uri = Uri(
      s"https://insights-collector.eu01.nr-data.net/v1/accounts/${accountId}/events"
    )
//    val vendorUrl: Uri = Uri.from(scheme = "http", host = "localhost", port = 9000)

    val memberEventAdapter = context.messageAdapter[MemberEvent]({
      case MemberJoined(member) =>
        ClusterChanged("join", member.uniqueAddress.toString)
      case MemberUp(member) =>
        ClusterChanged("up", member.uniqueAddress.toString)
      case MemberDowned(member) =>
        ClusterChanged("down", member.uniqueAddress.toString)
      case MemberLeft(member) =>
        ClusterChanged("left", member.uniqueAddress.toString)
      case MemberExited(member) =>
        ClusterChanged("exited", member.uniqueAddress.toString)
      case MemberRemoved(member, previousStatus) =>
        ClusterChanged("removed", member.uniqueAddress.toString)
      case MemberWeaklyUp(member) =>
        ClusterChanged("weakly_up", member.uniqueAddress.toString)
      case memberEvent =>
        ClusterChanged("unhandled", memberEvent.member.uniqueAddress.toString)
    })

    val reachabilityAdapter = context.messageAdapter[ReachabilityEvent] {
      case UnreachableMember(member) =>
        ClusterChanged("unreachable", member.uniqueAddress.toString)
      case ReachableMember(member) =>
        ClusterChanged("reachable", member.uniqueAddress.toString)
    }

    def createEntity(status: String, node: String): HttpEntity.Strict = {
      val rawJson =
        s"""[{"eventType":"statusChanged","status":"${status}","node":"${node}"}]"""
      val payload = Gzip.encode(ByteString(rawJson))
      HttpEntity(ContentTypes.`application/json`, payload)
    }

    Cluster(context.system).subscriptions ! Subscribe(
      memberEventAdapter,
      classOf[MemberEvent]
    )

    Cluster(context.system).subscriptions ! Subscribe(
      reachabilityAdapter,
      classOf[ReachabilityEvent]
    )

    Behaviors.receiveMessage {
      case ClusterChanged(status, node) => {
        val eventRequest =
          HttpRequest(
            uri = vendorUrl,
            method = HttpMethods.POST,
            entity = createEntity(status, node)
          ).withHeaders(
              RawHeader("X-Insert-Key", apiKey),
              `Content-Encoding`(gzip)
            )

        context
          .pipeToSelf(
            Http()
              .singleRequest(eventRequest)
          ) {
            case Success(response) =>
              if (response.status == StatusCodes.OK) PushSuccessful
              else PushRejected(response.status.intValue())
            case Failure(exception) => PushFailed(exception)
          }
        Behaviors.same
      }
      case PushSuccessful => {
        context.log.info("Successfully push events")
        Behaviors.same
      }
      case PushRejected(code) => {
        context.log.warn(s"Push requested with code ${code}")
        Behaviors.same
      }
      case PushFailed(ex) => {
        context.log.error("Push failed", ex)
        Behaviors.same
      }
    }
  }
}
