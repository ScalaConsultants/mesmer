package akka.persistence;

import akka.actor.typed.scaladsl.ActorContext;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.RecoveryStartedImpl;
import net.bytebuddy.asm.Advice;

public class RecoveryStartedAdvice {


    @Advice.OnMethodEnter
    public static void onStarted(@Advice.Argument(0) ActorContext<?> context, @Advice.This Object self) {
        RecoveryStartedImpl.enter(context, self);
    }


}
