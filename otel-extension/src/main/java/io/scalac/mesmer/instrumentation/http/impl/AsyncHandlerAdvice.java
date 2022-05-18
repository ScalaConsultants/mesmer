package io.scalac.mesmer.instrumentation.http.impl;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.Materializer;
import io.scalac.mesmer.otelextension.instrumentations.akka.http.UpdateHttpRouteWrapper;
import net.bytebuddy.asm.Advice;
import scala.Function1;
import scala.concurrent.Future;

public class AsyncHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void wrapHandler(
      @Advice.Argument(value = 0, readOnly = false)
          Function1<HttpRequest, Future<HttpResponse>> handler,
      @Advice.Argument(7) Materializer materialzier) {
    handler = new UpdateHttpRouteWrapper(handler, materialzier.executionContext());
  }
}
