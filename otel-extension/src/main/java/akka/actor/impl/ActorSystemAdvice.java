package akka.actor.impl;

import akka.actor.ActorSystem;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.ActorSystemMetricsActor;
import net.bytebuddy.asm.Advice;

public class ActorSystemAdvice {

  @Advice.OnMethodExit
  public static void applyExit(@Advice.Return ActorSystem system) {
    // ActorSystemMetricsBehavior.subscribeToEventStream(system);
    System.out.println("ActorSystemAdvice");
    ActorSystemMetricsActor.subscribeToEventStream(system);
  }
}
