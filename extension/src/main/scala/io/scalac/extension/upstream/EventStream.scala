package io.scalac.extension.upstream

import io.scalac.extension.model.Event
import NewRelicEventStream._
import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.headers.HttpEncodings.gzip
import akka.http.scaladsl.model.headers.{RawHeader, `Content-Encoding`}
import akka.http.scaladsl.model.{
  ContentTypes,
  HttpEntity,
  HttpMethods,
  HttpRequest,
  Uri
}
import akka.stream.{OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl._
import akka.util.ByteString
import com.typesafe.config.Config
import io.scalac.extension.model.Event.ClusterChangedEvent

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import io.scalac.extension.config.ConfigurationUtils._

trait EventStream[-T] {

  def push(event: T): Future[Unit]
}

class NewRelicEventStream(val config: NewRelicConfig)(
  implicit val system: ActorSystem
) extends EventStream[Event] {
  import config._
  import system.dispatcher

  private val newRelicUri = Uri(
    s"https://insights-collector.eu01.nr-data.net/v1/accounts/${accountId}/events"
  )

  private def createEntity(
    clusterChangedEvents: Seq[Event]
  ): HttpEntity.Strict = {
    val rawJson = clusterChangedEvents
      .map {
        case ClusterChangedEvent(status, node) =>
          s"""{"eventType":"statusChanged","status":"${status}","node":"${node}"}"""
      }
      .mkString("[", ",", "]")

    val payload = Gzip.encode(ByteString(rawJson))
    HttpEntity(ContentTypes.`application/json`, payload)
  }

  private lazy val newRelicQueue = {
    val source = Source.queue[Event](1024, OverflowStrategy.backpressure)

    val connection = Http()
      .outgoingConnectionHttps("insights-collector.eu01.nr-data.net")

    val queue = source
      .groupedWithin(1000, 5000 millis)
      .map(createEntity)
      .map(
        entity =>
          HttpRequest(
            uri = newRelicUri,
            method = HttpMethods.POST,
            entity = entity
          ).withHeaders(
            RawHeader("X-Insert-Key", apiKey),
            `Content-Encoding`(gzip)
        )
      )
      .via(connection)
      .toMat(Sink.ignore)(Keep.left)
      .run()
    queue
  }

  override def push(event: Event): Future[Unit] = {
    newRelicQueue
      .offer(event)
      .flatMap {
        case QueueOfferResult.Enqueued => Future.successful(())
        case QueueOfferResult.Dropped =>
          Future.failed(new RuntimeException("Internal buffer overflown"))
        case QueueOfferResult.QueueClosed =>
          Future.failed(new IllegalStateException("Queue closed"))
        case QueueOfferResult.Failure(ex) => Future.failed(ex)
      }
  }

  def shutdown(): Future[Done] = {
    newRelicQueue.complete()
    newRelicQueue.watchCompletion()
  }
}

object NewRelicEventStream {
  case class NewRelicConfig(apiKey: String, accountId: String)

  object NewRelicConfig {
    def fromConfig(config: Config): Either[String, NewRelicConfig] = {
      for {
        nrConfig <- config
          .tryValue("newrelic")(_.getConfig)
        apiKey <- nrConfig.tryValue("api_key")(_.getString)
        accountId <- nrConfig.tryValue("account_id")(_.getString)
      } yield NewRelicConfig(apiKey, accountId)

    }
  }

}
