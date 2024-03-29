package akka.actor.impl;

import akka.actor.ActorSystem;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.ActorLifecycleMonitorExtension;
import net.bytebuddy.asm.Advice;

public class ActorMetricsExtensionAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ActorSystem classicSystem) {
    ActorLifecycleMonitorExtension.registerExtension(classicSystem);
  }
}
