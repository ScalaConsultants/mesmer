package io.scalac.mesmer.instrumentation.http;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.scaladsl.Flow;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.AkkaHttpConnectionsInstrumentation;
import net.bytebuddy.asm.Advice;

public class HttpExtConnectionAdvice {

  @Advice.OnMethodEnter
  public static void bindAndHandle(
      @Advice.Argument(value = 0, readOnly = false) Flow<HttpRequest, HttpResponse, Object> handler,
      @Advice.Argument(1) String _interface,
      @Advice.Argument(2) Integer port) {
    handler =
        AkkaHttpConnectionsInstrumentation.bindAndHandleConnectionsImpl(handler, _interface, port);
  }
}
