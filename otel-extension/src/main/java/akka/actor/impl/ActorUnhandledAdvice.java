package akka.actor.impl;

import akka.actor.Actor;
import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ActorUnhandledAdvice {

  @Advice.OnMethodExit
  public static void exit(@Advice.This Actor self) {
    ActorContext context = self.context();
    Attributes attrs = VirtualField.find(ActorContext.class, Attributes.class).get(context);
    Instruments instruments =
        VirtualField.find(ActorSystem.class, Instruments.class).get(context.system());
    ActorCellInstrumentationState state =
        VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class).get(context);

    if (Objects.nonNull(attrs) && Objects.nonNull(state) && Objects.nonNull(instruments)) {
      instruments.unhandled().add(1L, attrs);
    }
  }
}
