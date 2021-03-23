package akka;

import akka.actor.Actor;
import io.scalac.agent.akka.stream.ActorGraphInterpreterOps;
import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;


public class ActorGraphInterpreterAdvice {


    @Advice.OnMethodExit
    public static void overrideReceive(@Advice.Return(readOnly = false) PartialFunction<Object, BoxedUnit> result,
                                       @Advice.This Actor self) {
        result = ActorGraphInterpreterOps.addCollectionReceive(result, self);
    }

}
