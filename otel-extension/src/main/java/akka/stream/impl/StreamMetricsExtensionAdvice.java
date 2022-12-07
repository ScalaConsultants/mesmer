package akka.stream.impl;

import akka.actor.ActorSystem;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.AkkaStreamMonitor;
import net.bytebuddy.asm.Advice;

public class StreamMetricsExtensionAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ActorSystem classicSystem) {
    AkkaStreamMonitor.registerExtension(classicSystem);
  }
}
