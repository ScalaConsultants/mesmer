package io.scalac.mesmer.agent.akka.newhttp;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.*;

public class AkkaHttpSingletons {

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.new-akka-http-mesmer";

  static {
    TextMapGetter<HttpRequest> serverHeaders = new AkkaHttpServerHeadersGetter();
    AkkaHttpAttributesGetter attributesGetter = new AkkaHttpAttributesGetter();

    System.out.println("CREATING THE INSTRUMENTER");

    INSTRUMENTER =
        Instrumenter.<HttpRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(attributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(attributesGetter))
            .addAttributesExtractor(HttpServerAttributesExtractor.create(attributesGetter))
            .addRequestMetrics(MesmerHttpMetrics.get())
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(serverHeaders);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    System.out.println("CALLING THE INSTRUMENTER");
    return INSTRUMENTER;
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }
}
