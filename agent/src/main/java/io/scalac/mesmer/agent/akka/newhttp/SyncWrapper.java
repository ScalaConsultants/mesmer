package io.scalac.mesmer.agent.akka.newhttp;

import static io.scalac.mesmer.agent.akka.newhttp.AkkaHttpSingletons.errorResponse;
import static io.scalac.mesmer.agent.akka.newhttp.AkkaHttpSingletons.instrumenter;
import static io.scalac.mesmer.agent.akka.newhttp.Java8BytecodeBridge.currentContext;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import scala.Function1;
import scala.runtime.AbstractFunction1;

public class SyncWrapper extends AbstractFunction1<HttpRequest, HttpResponse> {
  private final Function1<HttpRequest, HttpResponse> userHandler;

  public SyncWrapper(Function1<HttpRequest, HttpResponse> userHandler) {
    this.userHandler = userHandler;
  }

  @Override
  public HttpResponse apply(HttpRequest request) {

    System.out.println("In Sync Wrapper");

    Context parentContext = currentContext();
    if (!instrumenter().shouldStart(parentContext, request)) {
      return userHandler.apply(request);
    }
    Context context = instrumenter().start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      HttpResponse response = userHandler.apply(request);
      instrumenter().end(context, request, response, null);
      return response;
    } catch (Throwable t) {
      instrumenter().end(context, request, errorResponse(), t);
      throw t;
    }
  }
}
