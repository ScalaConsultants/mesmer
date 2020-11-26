package io.scalac.agent.akka.http

import java.lang.reflect.Method
import java.util.UUID

import _root_.akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import _root_.akka.http.scaladsl.server.{ ExceptionHandler, RejectionHandler, Route, RoutingLog }
import _root_.akka.http.scaladsl.settings.{ ParserSettings, RoutingSettings, ServerSettings }
import _root_.akka.stream.{ FlowShape, Materializer, SystemMaterializer }
import akka.actor.typed.scaladsl.adapter._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{ ConnectionContext, HttpExt }
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Source, Zip }
import io.scalac.agent.util.FunctionOps._
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent._
import net.bytebuddy.implementation.bind.annotation._

import scala.concurrent.{ ExecutionContextExecutor, Future }

class RouteInstrumentation
object RouteInstrumentation {

  def asyncHandler(
    route: Route,
    routingSettings: RoutingSettings,
    parserSettings: ParserSettings,
    materializer: Materializer,
    routingLog: RoutingLog,
    executionContext: ExecutionContextExecutor,
    rejectionHandler: RejectionHandler,
    exceptionHandler: ExceptionHandler,
    @SuperMethod method: Method,
    @This self: Any
  ): HttpRequest => Future[HttpResponse] = {

    // this is not ideal - java reflections are not well optimized by JIT

    materializer.asInstanceOf[SystemMaterializer]
    method
      .invoke(
        self,
        route.latency(millis => println(s"Request took ${millis} millis"))(
          executionContext
        ),
        routingSettings,
        parserSettings,
        materializer,
        routingLog,
        executionContext,
        rejectionHandler,
        exceptionHandler
      )
      .asInstanceOf[HttpRequest => Future[HttpResponse]]
  }
}

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

      zipRequest.out.map {
        case (request, id) => {
          EventBus(system.toTyped).publishEvent(
            RequestStarted(id, System.currentTimeMillis(), request.uri.path.toString(), request.method.value)
          )
          request
        }
      } ~> flow

      flow ~> zipRespone.in0
      idBroadcast ~> zipRespone.in1

      zipRespone.out.map {
        case (response, id) => {
          EventBus(system.toTyped).publishEvent(RequestCompleted(id, System.currentTimeMillis()))
          response
        }
      } ~> outerResponse.in

      FlowShape(outerRequest.in, outerResponse.out)

    })
    println("DOING HTTP BINDING")

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
