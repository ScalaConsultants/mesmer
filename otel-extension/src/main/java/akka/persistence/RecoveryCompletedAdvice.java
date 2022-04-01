package akka.persistence;

import akka.actor.typed.scaladsl.ActorContext;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.RecoveryCompletedImpl;
import net.bytebuddy.asm.Advice;

public class RecoveryCompletedAdvice {


    @Advice.OnMethodEnter
    public static void onStarted(@Advice.Argument(0) ActorContext<?> context, @Advice.This Object self) {
        System.out.println("COMPLETED FIRST");
        RecoveryCompletedImpl.enter(context, self);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void onExit(@Advice.Thrown(readOnly = false) Throwable error) {
        if(error != null) {
            System.out.println("ERROR OCCURRED " + error.getMessage());
            error = null;
        } else {
            System.out.println("FINISHED SUCCESSFULLY COMPLETING RECOVERY");
        }
    }
}
