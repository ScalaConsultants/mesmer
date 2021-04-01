package io.scalac.agent.akka.persistence;

import akka.actor.typed.Signal;
import net.bytebuddy.asm.Advice;
import scala.PartialFunction;
import scala.Tuple2;
import scala.runtime.BoxedUnit;

public class SnapshotAdvice {
    @Advice.OnMethodEnter
    public static void onConstructorEnter(@Advice.Argument(value = 14, readOnly = false) PartialFunction<Tuple2<Object, Signal>, BoxedUnit> singnalHandler) {
        singnalHandler = SnapshotSignalInterceptor.constructorAdvice(singnalHandler);
    }
}
