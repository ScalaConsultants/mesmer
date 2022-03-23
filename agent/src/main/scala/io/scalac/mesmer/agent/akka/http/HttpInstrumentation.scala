package io.scalac.mesmer.agent.akka.http

import _root_.akka.http.scaladsl.model.HttpRequest
import _root_.akka.http.scaladsl.model.HttpResponse
import _root_.akka.stream.BidiShape
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.HttpExt
import akka.stream.scaladsl.BidiFlow
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Zip
import io.opentelemetry.api.common.Attributes

import io.scalac.mesmer.core.akka.stream.BidiFlowForward
import io.scalac.mesmer.core.event.EventBus
import io.scalac.mesmer.core.event.HttpEvent._
import io.scalac.mesmer.core.model._
import io.scalac.mesmer.core.util.Timestamp

object HttpInstrumentation {

  final class RandomIdGenerator(val prefix: String) {
    private[this] var id: Long = 0L

    def next(): String = {
      val value = new StringBuilder(prefix.length + 10)
        .append(prefix)
        .append(id)
        .toString()
      id += 1L
      value
    }
  }

  def bindAndHandleRequestImpl(
    handler: Flow[HttpRequest, HttpResponse, Any],
    self: HttpExt
  ): Flow[HttpRequest, HttpResponse, Any] = {
    val system = self.system.toTyped

    val requestIdFlow =
      BidiFlow.fromGraph[HttpRequest, HttpRequest, HttpResponse, HttpResponse, Any](GraphDSL.create() {
        implicit builder =>
          import GraphDSL.Implicits._

          val local = new ThreadLocal[RandomIdGenerator] {
            override def initialValue(): RandomIdGenerator =
              new RandomIdGenerator(Thread.currentThread().getName + Thread.currentThread().getId)
          }

          val outerRequest  = builder.add(Flow[HttpRequest])
          val outerResponse = builder.add(Flow[HttpResponse])
          val idGenerator = Source
            .repeat(())
            .map(_ => local.get.next())

          val zipRequest  = builder.add(Zip[HttpRequest, String]())
          val zipResponse = builder.add(Zip[HttpResponse, String]())

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

          idBroadcast ~> zipResponse.in1

          zipResponse.out.map { case (response, id) =>
            EventBus(system).publishEvent(
              RequestCompleted(id, Timestamp.create(), response.status.intValue().toString)
            )
            response
          } ~> outerResponse.in

          BidiShape(outerRequest.in, outerRequestOut.outlet, zipResponse.in0, outerResponse.out)
      })

    requestIdFlow
      .join(handler)

  }

  def bindAndHandleConnectionsImpl(
    handler: Flow[HttpRequest, HttpResponse, Any],
    interface: String,
    port: java.lang.Integer,
    self: HttpExt
  ): Flow[HttpRequest, HttpResponse, Any] = {
    val system = self.system.toTyped

    val attributes = Attributes
      .builder()
      .put("interface", interface)
      .put("port", port.toLong)
      .build()

    val connectionsCountFlow = BidiFlowForward[HttpRequest, HttpResponse](
      onPreStart = () => {
        HttpInstruments.connectionTotalCounter.add(1, attributes)
        EventBus(system).publishEvent(ConnectionStarted(interface, port))
      },
      onPostStop = () => {
        HttpInstruments.connectionTotalCounter.add(-1, attributes)
        EventBus(system).publishEvent(ConnectionCompleted(interface, port))
      }
    )
    connectionsCountFlow.join(handler)
  }
}
