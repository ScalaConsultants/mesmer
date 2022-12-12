package akka.stream.impl;

import akka.actor.ActorSystem;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitorExtension;
import net.bytebuddy.asm.Advice;

public class StreamMetricsExtensionAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ActorSystem classicSystem) {
    AkkaStreamMonitorExtension.registerExtension(classicSystem);
  }
}
