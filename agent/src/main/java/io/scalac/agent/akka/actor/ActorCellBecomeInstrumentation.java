package io.scalac.agent.akka.actor;

import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.runtime.BoxedUnit;

public class ActorCellBecomeInstrumentation {

    @Advice.OnMethodEnter
    public static void onEnter(@Advice.This Object actorCell, @Advice.Argument(value = 0, readOnly = false) PartialFunction<Object, BoxedUnit> receive) {
        receive = new WrappedReceive(receive, actorCell);
    }

}
