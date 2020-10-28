package io.scalac.extension

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.cluster.ClusterEvent._
import akka.cluster.typed.{Cluster, Subscribe}
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.HttpEncodings.gzip
import akka.http.scaladsl.model.headers.{RawHeader, `Content-Encoding`}
import akka.util.ByteString

import scala.util.{Failure, Success}


object ListeningActor {

  sealed trait Command

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
