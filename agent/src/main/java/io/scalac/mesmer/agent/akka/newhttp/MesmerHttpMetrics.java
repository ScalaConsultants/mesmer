package io.scalac.mesmer.agent.akka.newhttp;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.instrumentation.api.instrumenter.RequestMetrics;

public class MesmerHttpMetrics implements RequestListener {

  private final LongUpDownCounter activeRequests;

  public static RequestMetrics get() {
    return MesmerHttpMetrics::new;
  }

  public MesmerHttpMetrics(Meter meter) {
    activeRequests =
        meter
            .upDownCounterBuilder("mesmer.http.server.active_requests")
            .setUnit("requests")
            .setDescription("The number of concurrent HTTP requests that are currently in-flight")
            .build();
  }

  ContextKey<String> rt = ContextKey.named("target");

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    String target = startAttributes.get(AttributeKey.stringKey("http.target"));
    activeRequests.add(1, buildAttributes(target), context);
    return context.with(rt, target);
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    String target = context.get(rt);
    activeRequests.add(-1, buildAttributes(target));
  }

  private Attributes buildAttributes(String target) {
    return Attributes.builder().put("http_request_target", target).build();
  }
}
