package io.scalac.agent.akka.http

import java.lang.reflect.Method
import java.util.UUID

import scala.concurrent.Future

import _root_.akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import _root_.akka.http.scaladsl.settings.ServerSettings
import _root_.akka.stream.{ FlowShape, Materializer }
import akka.actor.typed.scaladsl.adapter._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{ ConnectionContext, HttpExt }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Source, Zip }

import net.bytebuddy.implementation.bind.annotation._

import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent._

class HttpInstrumentation
object HttpInstrumentation {

  def bindAndHandle(
    handler: Flow[HttpRequest, HttpResponse, Any],
    interface: String,
    port: java.lang.Integer,
    connectionContext: ConnectionContext,
    settings: ServerSettings,
    log: LoggingAdapter,
    mat: Materializer,
    @SuperMethod method: Method,
    @This self: Any
  ): Future[ServerBinding] = {
    implicit val system = self.asInstanceOf[HttpExt].system

    val newHandler = Flow.fromGraph[HttpRequest, HttpResponse, Any](GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val outerRequest  = builder.add(Flow[HttpRequest])
      val outerResponse = builder.add(Flow[HttpResponse])
      val flow          = builder.add(handler)
      val idGenerator = Source
        .repeat(())
        .map(_ => UUID.randomUUID().toString)

      val zipRequest = builder.add(Zip[HttpRequest, String])
      val zipRespone = builder.add(Zip[HttpResponse, String])

      val idBroadcast = builder.add(Broadcast[String](2))

      idGenerator ~> idBroadcast.in

      outerRequest ~> zipRequest.in0
      idBroadcast ~> zipRequest.in1

      System.nanoTime()

      zipRequest.out.map { case (request, id) =>
        val path   = request.uri.path.toString()
        val method = request.method.value
        EventBus(system.toTyped).publishEvent(RequestStarted(id, Timestamp.create(), path, method))
        request
      } ~> flow

      flow ~> zipRespone.in0
      idBroadcast ~> zipRespone.in1

      zipRespone.out.map { case (response, id) =>
        EventBus(system.toTyped).publishEvent(RequestCompleted(id, Timestamp.create()))
        response
      } ~> outerResponse.in

      FlowShape(outerRequest.in, outerResponse.out)

    })

    method
      .invoke(
        self,
        newHandler,
        interface,
        port,
        connectionContext,
        settings,
        log,
        mat
      )
      .asInstanceOf[Future[ServerBinding]]
  }
}
