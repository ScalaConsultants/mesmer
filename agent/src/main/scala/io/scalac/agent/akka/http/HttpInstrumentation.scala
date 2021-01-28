package io.scalac.agent.akka.http

import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.FlowShape
import akka.stream.scaladsl.{ Broadcast, Flow, GraphDSL, Source, Zip }
import io.scalac.core.util.Timestamp
import io.scalac.extension.event.EventBus
import io.scalac.extension.event.HttpEvent.{ RequestCompleted, RequestStarted }

class HttpInstrumentation
object HttpInstrumentation {

  class RandomIdGenerator(val prefix: String) {
    private[this] var id: Long = 0L

    def next(): String = {
      val value = new StringBuilder(prefix.size + 10)
        .append(prefix)
        .append(id)
        .toString()
      id += 1L // TODO check for overflow
      value
    }
  }

  def bindAndHandleImpl(
    handler: Flow[HttpRequest, HttpResponse, Any],
    self: HttpExt
  ): Flow[HttpRequest, HttpResponse, Any] = {
    implicit val system = self.system

    val newHandler = Flow.fromGraph[HttpRequest, HttpResponse, Any](GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val local = new ThreadLocal[RandomIdGenerator] {
        override def initialValue(): RandomIdGenerator =
          new RandomIdGenerator(Thread.currentThread().getName + Thread.currentThread().getId)
      }

      val outerRequest  = builder.add(Flow[HttpRequest])
      val outerResponse = builder.add(Flow[HttpResponse])
      val flow          = builder.add(handler)
      val idGenerator = Source
        .repeat(())
        .map(_ => local.get.next())

      val zipRequest = builder.add(Zip[HttpRequest, String])
      val zipRespone = builder.add(Zip[HttpResponse, String])

      val idBroadcast = builder.add(Broadcast[String](2))

      idGenerator ~> idBroadcast.in

      outerRequest ~> zipRequest.in0
      idBroadcast ~> zipRequest.in1

      zipRequest.out.map {
        case (request, id) => {
          val path   = request.uri.path.toString()
          val method = request.method.value
          EventBus(system.toTyped).publishEvent(RequestStarted(id, Timestamp.create(), path, method))
          request
        }
      } ~> flow

      flow ~> zipRespone.in0
      idBroadcast ~> zipRespone.in1

      zipRespone.out.map {
        case (response, id) => {
          EventBus(system.toTyped).publishEvent(RequestCompleted(id, Timestamp.create()))
          response
        }
      } ~> outerResponse.in

      FlowShape(outerRequest.in, outerResponse.out)

    })
    newHandler
  }
}
