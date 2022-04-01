package akka.persistence;

import akka.actor.typed.scaladsl.ActorContext;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.RecoveryCompletedImpl;
import net.bytebuddy.asm.Advice;

public class RecoveryCompletedAdvice {


    @Advice.OnMethodEnter
    public static void onStarted(@Advice.Argument(0) ActorContext<?> context, @Advice.This Object self) {
        RecoveryCompletedImpl.enter(context, self);
    }


}
