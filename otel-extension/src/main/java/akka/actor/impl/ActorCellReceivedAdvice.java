package akka.actor.impl;

import akka.actor.ActorContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ActorCellReceivedAdvice {

  @Advice.OnMethodEnter
  public static long enter(@Advice.This ActorContext self) {

    return System.nanoTime();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class)
  public static void exit(
      @Advice.This ActorContext self,
      @Advice.Thrown Throwable exception,
      @Advice.Enter long started) {
    long interval = System.nanoTime() - started;

    Attributes attrs = VirtualField.find(ActorContext.class, Attributes.class).get(self);
    ActorCellInstrumentationState state =
        VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class).get(self);
    if (Objects.nonNull(attrs) && Objects.nonNull(state)) {
      Instruments.processingTime().record(interval, attrs);

      /*
         Here we check it there was an exception and if TypedInstrumentation already taken care of this
      */
      if (Objects.nonNull(exception) && !state.getAndResetFailed()) {
        Instruments.failedMessages().add(1L, attrs);
      }
    }
  }
}
