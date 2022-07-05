package akka.actor.impl;

import akka.actor.ActorSystem;
import akka.actor.ClassicActorSystemProvider;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.actor.ActorRefConfiguration;
import io.scalac.mesmer.core.actor.DefaultActorRefConfiguration;
import io.scalac.mesmer.core.actor.WithSystemActorRefConfigurator;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ClassicActorSystemProviderAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ClassicActorSystemProvider provider) {

    ActorRefConfiguration config =
        VirtualField.find(ActorSystem.class, ActorRefConfiguration.class)
            .get(provider.classicSystem());
    if (Objects.isNull(config)) {

      VirtualField.find(ActorSystem.class, ActorRefConfiguration.class)
          .set(
              provider.classicSystem(),
              new WithSystemActorRefConfigurator(provider, DefaultActorRefConfiguration.self()));
    }
  }
}
