package akka.actor.impl;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.ClassicActorSystemProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import net.bytebuddy.asm.Advice;

public class ActorCellInitAdvice {

  @Advice.OnMethodExit
  public static void initActorCell(
      @Advice.This ActorContext self,
      @Advice.FieldValue("system") ClassicActorSystemProvider system) {
    Instruments instruments =
        VirtualField.find(ActorSystem.class, Instruments.class).get(system.classicSystem());

    if (instruments != null) {
      Attributes attributes = instruments.config().forClassic(self.self()).build();

      VirtualField.find(ActorContext.class, Attributes.class).set(self, attributes);
    }
    VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class)
        .set(self, new ActorCellInstrumentationState());
  }
}
