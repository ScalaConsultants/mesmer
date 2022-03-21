package io.scalac.mesmer.agent.akka.newhttp;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.instrumenter.RequestListener;
import io.opentelemetry.instrumentation.api.instrumenter.RequestMetrics;
import java.io.Serializable;

public class MesmerHttpMetrics implements RequestListener {

  private final LongUpDownCounter activeRequests;

  private final DoubleHistogram duration;

  private final LongCounter notFoundCounter;

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

    duration =
        meter
            .histogramBuilder("mesmer.http.server.duration")
            .setUnit("ms")
            .setDescription("The duration of the inbound HTTP request")
            .build();

    notFoundCounter =
        meter
            .counterBuilder("mesmer.http.server.notfoundcounter")
            .setUnit("quantity")
            .setDescription("How many 404 happened so far?")
            .build();
  }

  ContextKey<State> stateContextKey = ContextKey.named("state");

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    String target = startAttributes.get(AttributeKey.stringKey("http.target"));
    State state = new State(target, startNanos);
    activeRequests.add(1, buildAttributes(state.target), context);

    return context.with(stateContextKey, state);
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    State state = context.get(stateContextKey);
    Attributes attributes = buildAttributes(state.target);
    activeRequests.add(-1, attributes);

    double requestDuration = endNanos - state.startTimeNanos / 1000D;

    // TODO:  We should use the status as an attribute.
    Long status = endAttributes.get(AttributeKey.longKey("http.status_code"));
    if (status == 404) {
      notFoundCounter.add(1);
    }

    duration.record(requestDuration, attributes);
  }

  private Attributes buildAttributes(String target) {
    return Attributes.builder().put("http_request_target", target).build();
  }

  static class State implements Serializable {
    String target;
    Long startTimeNanos;

    public State(String target, Long startTimeNanos) {
      this.target = target;
      this.startTimeNanos = startTimeNanos;
    }
  }
}
