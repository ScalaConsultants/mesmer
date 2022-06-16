package akka.actor.impl;

import akka.actor.ActorSystem;
import akka.actor.ClassicActorSystemProvider;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.actor.DefaultActorRefConfiguration;
import io.scalac.mesmer.core.actor.WithSystemActorRefConfigurator;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ClassicActorSystemProviderAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ClassicActorSystemProvider provider) {

    Instruments instruments =
        VirtualField.find(ActorSystem.class, Instruments.class).get(provider.classicSystem());
    if (Objects.isNull(instruments)) {
      System.out.println("Setting up instruments ");
      Instruments concreteInstruments =
          new Instruments(
              new WithSystemActorRefConfigurator(provider, DefaultActorRefConfiguration.self()),
              GlobalOpenTelemetry.getMeterProvider());
      VirtualField.find(ActorSystem.class, Instruments.class)
          .set(provider.classicSystem(), concreteInstruments);
      concreteInstruments.printVF();
    }
  }
}
