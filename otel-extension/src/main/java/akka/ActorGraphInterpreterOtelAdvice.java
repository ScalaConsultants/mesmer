package akka;

import akka.actor.Actor;
import io.scalac.mesmer.otelextension.instrumentations.akka.stream.impl.ActorGraphInterpreterOtelDecorator;
import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public class ActorGraphInterpreterOtelAdvice {

  @Advice.OnMethodExit
  public static void overrideReceive(
      @Advice.Return(readOnly = false) PartialFunction<Object, BoxedUnit> result,
      @Advice.This Object self) {
    System.out.println("OVERRIDING RECEIVE");
    result = ActorGraphInterpreterOtelDecorator.addCollectionReceive(result, (Actor) self);
  }
}
