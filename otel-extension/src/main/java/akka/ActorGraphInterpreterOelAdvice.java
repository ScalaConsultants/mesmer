package akka;

import akka.actor.Actor;
import io.scalac.mesmer.agentcopy.akka.stream.impl.ActorGraphInterpreterOtelDecorator;
import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public class ActorGraphInterpreterOelAdvice {

  @Advice.OnMethodExit
  public static void overrideReceive(
      @Advice.Return(readOnly = false) PartialFunction<Object, BoxedUnit> result,
      @Advice.This Object self) {
    result = ActorGraphInterpreterOtelDecorator.addCollectionReceive(result, (Actor) self);
  }
}
