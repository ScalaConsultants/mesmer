package io.scalac.mesmer.agent.akka.newhttp;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.context.Context;
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

  @Override
  public Context start(Context context, Attributes startAttributes, long startNanos) {
    activeRequests.add(1, startAttributes, context);

    System.out.println("FOFOOOFOFOFOOF");
    // That does not modify context - just passes it further. Should we do anything with the
    // context???
    return context;
  }

  @Override
  public void end(Context context, Attributes endAttributes, long endNanos) {
    System.out.println("Oooooooooo");

    activeRequests.add(-1, endAttributes);
  }
}
