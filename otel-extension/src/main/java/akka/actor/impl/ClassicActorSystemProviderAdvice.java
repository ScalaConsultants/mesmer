package akka.actor.impl;

import akka.actor.ActorSystem;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.scalac.mesmer.core.actor.ActorRefAttributeFactory;
import io.scalac.mesmer.core.actor.ConfiguredAttributeFactory;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ClassicActorSystemProviderAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ActorSystem classicSystem) {

    ActorRefAttributeFactory config =
        VirtualField.find(ActorSystem.class, ActorRefAttributeFactory.class).get(classicSystem);

    if (Objects.isNull(config)) {

      VirtualField.find(ActorSystem.class, ActorRefAttributeFactory.class)
          .set(
              classicSystem,
              new ConfiguredAttributeFactory(classicSystem.settings().config(), classicSystem));
    }
  }
}
