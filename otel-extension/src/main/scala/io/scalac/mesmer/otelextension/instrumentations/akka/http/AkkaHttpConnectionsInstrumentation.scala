package io.scalac.mesmer.otelextension.instrumentations.akka.http

import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.Flow
import io.scalac.mesmer.core.akka.stream.BidiFlowForward

object AkkaHttpConnectionsInstrumentation {
  def bindAndHandleConnectionsImpl(
    handler: Flow[HttpRequest, HttpResponse, Any],
    interface: String,
    port: java.lang.Integer
  ): Flow[HttpRequest, HttpResponse, Any] = {

    val connectionsCountFlow = BidiFlowForward[HttpRequest, HttpResponse](
      onPreStart = () => println(s"on pre start $interface:$port"),
      onPostStop = () => println(s"on post stop $interface:$port")
    )
    connectionsCountFlow.join(handler)
  }
}
