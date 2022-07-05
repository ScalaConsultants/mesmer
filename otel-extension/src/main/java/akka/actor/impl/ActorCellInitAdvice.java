package akka.actor.impl;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.ClassicActorSystemProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.actor.ActorRefConfiguration;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import net.bytebuddy.asm.Advice;

public class ActorCellInitAdvice {

  @Advice.OnMethodExit
  public static void initActorCell(
      @Advice.This ActorContext self,
      @Advice.FieldValue("system") ClassicActorSystemProvider system) {
    ActorRefConfiguration config =
        VirtualField.find(ActorSystem.class, ActorRefConfiguration.class)
            .get(system.classicSystem());

    if (config != null) {
      Attributes attributes = config.forClassic(self.self()).build();

      VirtualField.find(ActorContext.class, Attributes.class).set(self, attributes);
    }
    VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class)
        .set(self, new ActorCellInstrumentationState());
  }
}
