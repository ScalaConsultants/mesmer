package akka.stream.impl;

import akka.actor.Actor;
import akka.stream.impl.fusing.ActorGraphInterpreter;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator;
import net.bytebuddy.asm.Advice;

public class ActorGraphInterpreterProcessEventAdvice {

  @Advice.OnMethodExit
  public static void processEvent(
      @Advice.This Object self,
      @Advice.Argument(0) ActorGraphInterpreter.BoundaryEvent boundaryEvent) {

    System.out.println("working ActorGraphInterpreterProcessEventAdvice");
    if (boundaryEvent.shell().isTerminated()) {
      ActorGraphInterpreterOtelDecorator.shellFinished(boundaryEvent.shell(), (Actor) self);
    }
  }
}
