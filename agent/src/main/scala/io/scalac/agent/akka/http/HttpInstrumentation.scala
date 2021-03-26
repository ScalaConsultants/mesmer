package io.scalac.agent.akka.http

import _root_.akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import _root_.akka.http.scaladsl.settings.ServerSettings
import _root_.akka.stream.{ Attributes, BidiShape, FlowShape, Inlet, Materializer, Outlet }
import akka.actor.typed.scaladsl.adapter._
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{ ConnectionContext, HttpExt }
import akka.stream.scaladsl.{ BidiFlow, Broadcast, Flow, GraphDSL, Source, Zip }

import io.scalac.core.model._
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent._
import net.bytebuddy.implementation.bind.annotation._
import java.lang.reflect.Method
import java.util.UUID

import scala.concurrent.Future

import akka.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

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

    val system = self.asInstanceOf[HttpExt].system.toTyped

    val connectionsCountFlow = BidiFlow.fromGraph(
      new GraphStage[BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse]] {

        private val requestIn   = Inlet.create[HttpRequest]("request.in")
        private val requestOut  = Outlet.create[HttpRequest]("request.out")
        private val responseIn  = Inlet.create[HttpResponse]("response.in")
        private val responseOut = Outlet.create[HttpResponse]("response.out")

        override val shape: BidiShape[HttpRequest, HttpRequest, HttpResponse, HttpResponse] =
          BidiShape(requestIn, requestOut, responseIn, responseOut)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
          // This following `setHandler`s are just to forward requests and responses.
          // The real value of this flow is `preStart` and postStop` hooks override.
          // The general handlers and its interactions is based on Kamon's akka instrumentation.

          setHandler(
            requestIn,
            new InHandler {
              override def onPush(): Unit           = push(requestOut, grab(requestIn))
              override def onUpstreamFinish(): Unit = complete(requestOut)
            }
          )

          setHandler(
            requestOut,
            new OutHandler {
              override def onPull(): Unit                             = pull(requestIn)
              override def onDownstreamFinish(cause: Throwable): Unit = cancel(requestIn)
            }
          )

          setHandler(
            responseIn,
            new InHandler {
              override def onPush(): Unit           = push(responseOut, grab(responseIn))
              override def onUpstreamFinish(): Unit = completeStage()
            }
          )

          setHandler(
            responseOut,
            new OutHandler {
              override def onPull(): Unit                             = pull(responseIn)
              override def onDownstreamFinish(cause: Throwable): Unit = cancel(responseIn)
            }
          )

          override def preStart(): Unit =
            EventBus(system).publishEvent(ConnectionStarted(interface, port))

          override def postStop(): Unit =
            EventBus(system).publishEvent(ConnectionCompleted(interface, port))

        }

      }
    )

    val requestIdFlow =
      BidiFlow.fromGraph[HttpRequest, HttpRequest, HttpResponse, HttpResponse, Any](GraphDSL.create() {
        implicit builder =>
          import GraphDSL.Implicits._
          val outerRequest  = builder.add(Flow[HttpRequest])
          val outerResponse = builder.add(Flow[HttpResponse])
          val idGenerator = Source
            .repeat(())
            .map(_ => UUID.randomUUID().toString)

          val zipRequest = builder.add(Zip[HttpRequest, String])
          val zipRespone = builder.add(Zip[HttpResponse, String])

          val idBroadcast = builder.add(Broadcast[String](2))

          idGenerator ~> idBroadcast.in

          outerRequest ~> zipRequest.in0
          idBroadcast ~> zipRequest.in1

          val outerRequestOut = zipRequest.out.map { case (request, id) =>
            val path   = request.uri.path.toPath
            val method = request.method.toMethod
            EventBus(system).publishEvent(RequestStarted(id, Timestamp.create(), path, method))
            request
          }

          idBroadcast ~> zipRespone.in1

          zipRespone.out.map { case (response, id) =>
            EventBus(system).publishEvent(
              RequestCompleted(id, Timestamp.create(), response.status.intValue().toString)
            )
            response
          } ~> outerResponse.in

          BidiShape(outerRequest.in, outerRequestOut.outlet, zipRespone.in0, outerResponse.out)
      })

    method
      .invoke(
        self,
        connectionsCountFlow
          .atop(requestIdFlow)
          .join(handler),
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
