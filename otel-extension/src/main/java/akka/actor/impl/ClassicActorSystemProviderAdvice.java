package akka.actor.impl;

import akka.actor.ClassicActorSystemProvider;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.actor.ActorRefConfiguration;
import io.scalac.mesmer.core.actor.DefaultActorRefConfiguration;
import io.scalac.mesmer.core.actor.WithSystemActorRefConfigurator;
import net.bytebuddy.asm.Advice;

public class ClassicActorSystemProviderAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ClassicActorSystemProvider system) {

    System.out.println("Initializing actor system");
    VirtualField.find(ClassicActorSystemProvider.class, ActorRefConfiguration.class)
        .set(
            system,
            new WithSystemActorRefConfigurator(system, DefaultActorRefConfiguration.self()));
  }
}
