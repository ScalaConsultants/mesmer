package akka;

import akka.actor.Actor;
import io.scalac.mesmer.agent.akka.stream.impl.ActorGraphInterpreterDecorator;
import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public class ActorGraphInterpreterAdvice {

  @Advice.OnMethodExit
  public static void overrideReceive(
      @Advice.Return(readOnly = false) PartialFunction<Object, BoxedUnit> result,
      @Advice.This Object self) {
    result = ActorGraphInterpreterDecorator.addCollectionReceive(result, (Actor) self);
  }
}
