package io.scalac.mesmer.agent.akka.newhttp;

import static io.scalac.mesmer.agent.akka.newhttp.AkkaHttpSingletons.errorResponse;
import static io.scalac.mesmer.agent.akka.newhttp.AkkaHttpSingletons.instrumenter;
import static io.scalac.mesmer.agent.akka.newhttp.Java8BytecodeBridge.currentContext;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import scala.Function1;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;

public class AsyncWrapper extends AbstractFunction1<HttpRequest, Future<HttpResponse>> {
  private final Function1<HttpRequest, Future<HttpResponse>> userHandler;
  private final ExecutionContext executionContext;

  public AsyncWrapper(
      Function1<HttpRequest, Future<HttpResponse>> userHandler, ExecutionContext executionContext) {
    this.userHandler = userHandler;
    this.executionContext = executionContext;
  }

  @Override
  public Future<HttpResponse> apply(HttpRequest request) {
    System.out.println("In Async Wrapper");

    Context parentContext = currentContext();
    if (!instrumenter().shouldStart(parentContext, request)) {
      return userHandler.apply(request);
    }
    Context context = instrumenter().start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      return userHandler
          .apply(request)
          .transform(
              new AbstractFunction1<HttpResponse, HttpResponse>() {
                @Override
                public HttpResponse apply(HttpResponse response) {
                  instrumenter().end(context, request, response, null);
                  return response;
                }
              },
              new AbstractFunction1<Throwable, Throwable>() {
                @Override
                public Throwable apply(Throwable t) {
                  instrumenter().end(context, request, errorResponse(), t);
                  return t;
                }
              },
              executionContext);
    } catch (Throwable t) {
      instrumenter().end(context, request, null, t);
      throw t;
    }
  }
}
