package akka.actor.impl;

import akka.actor.ActorContext;
import akka.actor.ClassicActorSystemProvider;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.actor.ActorRefConfiguration;
import java.util.stream.Collectors;
import net.bytebuddy.asm.Advice;

public class ActorCellInitAdvice {

  @Advice.OnMethodExit
  public static void initActorCell(
      @Advice.This ActorContext self,
      @Advice.FieldValue("system") ClassicActorSystemProvider system) {

    System.out.println("Initializing ref " + self.self().path().toStringWithoutAddress());
    ActorRefConfiguration configuration =
        VirtualField.find(ClassicActorSystemProvider.class, ActorRefConfiguration.class)
            .get(system);

    if (configuration != null) {
      Attributes attributes = configuration.forClassic(self.self()).build();

      String showAttributes =
          attributes.asMap().entrySet().stream()
              .map(v -> "( " + v.getKey().toString() + ", " + v.getValue().toString() + " )")
              .collect(Collectors.joining(", "));

      System.out.println("Init actor cell with attributes " + showAttributes);

      VirtualField.find(ActorContext.class, Attributes.class).set(self, attributes);
    }
  }
}
