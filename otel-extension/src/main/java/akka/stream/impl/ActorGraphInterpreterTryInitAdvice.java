package akka.stream.impl;

import akka.actor.Actor;
import akka.stream.impl.fusing.GraphInterpreterShell;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator;
import net.bytebuddy.asm.Advice;

/**
 * Instrumentation for short living streams - part of shell initialization is its execution if shell
 * is terminated. After that it is not added to activeInterpreters.
 */
public class ActorGraphInterpreterTryInitAdvice {

  @Advice.OnMethodExit
  public static void tryInit(
      @Advice.This Object self,
      @Advice.Argument(0) GraphInterpreterShell shell,
      @Advice.Return Boolean initialized) {

    System.out.println("working ActorGraphInterpreterTryInitOtelAdvice");

    if (!initialized) {
      ActorGraphInterpreterOtelDecorator.shellFinished(shell, (Actor) self);
    }
  }
}
