package akka;

import io.scalac.agent.akka.stream.AkkaStreamExtensions;
import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;


public class ActorGraphInterpreterAdvice {


    @Advice.OnMethodExit
    public static void overrideReceive(@Advice.Return(readOnly = false) PartialFunction<Object, BoxedUnit> result, @Advice.This Object self) {
        result = AkkaStreamExtensions.addCollectionReceive(result, self);
    }

}
