package akka.cluster.impl;

import akka.actor.ActorSystem;
import io.scalac.mesmer.otelextension.instrumentations.akka.cluster.extension.AkkaClusterMonitorExtension;
import net.bytebuddy.asm.Advice;

public class ClusterMetricsExtensionAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ActorSystem classicSystem) throws InterruptedException {
    AkkaClusterMonitorExtension.registerExtension(classicSystem);
  }
}
