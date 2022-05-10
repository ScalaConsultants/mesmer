package io.scalac.mesmer.otelextension.instrumentations.akka.http

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.stream.scaladsl.Flow
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.LongUpDownCounter

import io.scalac.mesmer.core.akka.stream.BidiFlowForward

object AkkaHttpConnectionsInstrumentation {
  def bindAndHandleConnectionsImpl(
    handler: Flow[HttpRequest, HttpResponse, Any],
    interface: String,
    port: Long
  ): Flow[HttpRequest, HttpResponse, Any] = {

    val attributes = Attributes
      .builder()
      .put("interface", interface)
      .put("port", port)
      .build()

    val connectionsCountFlow = BidiFlowForward[HttpRequest, HttpResponse](
      onPreStart = () => HttpConnectionInstruments.httpConnectionsCounter.add(1, attributes),
      onPostStop = () => HttpConnectionInstruments.httpConnectionsCounter.add(-1, attributes)
    )
    connectionsCountFlow.join(handler)
  }

  private object HttpConnectionInstruments {
    private val meter = GlobalOpenTelemetry.getMeter("mesmer")

    // TODO: pass name from configuration
    val httpConnectionsCounter: LongUpDownCounter = meter
      .upDownCounterBuilder("http.connections")
      .setDescription("Amount of HTTP connections currently being used")
      .build()
  }
}
