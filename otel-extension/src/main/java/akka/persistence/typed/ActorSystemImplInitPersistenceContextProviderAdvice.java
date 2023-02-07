package akka.persistence.typed;

import akka.actor.ActorSystem;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.scalac.mesmer.configuration.Config;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.IdentityPersistenceContextProvider;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContextProvider;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.TemplatingPersistenceContextProvider;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ActorSystemImplInitPersistenceContextProviderAdvice {

  @Advice.OnMethodExit
  public static void init(@Advice.This ActorSystem classicSystem) {

    PersistenceContextProvider provider =
        VirtualField.find(ActorSystem.class, PersistenceContextProvider.class).get(classicSystem);

    if (Objects.isNull(provider)) {

      if (Config.getBoolean("mesmer.akka.persistence.templated", true)) {

        VirtualField.find(ActorSystem.class, PersistenceContextProvider.class)
            .set(classicSystem, new TemplatingPersistenceContextProvider());
      } else {
        VirtualField.find(ActorSystem.class, PersistenceContextProvider.class)
            .set(classicSystem, new IdentityPersistenceContextProvider());
      }
    }
  }
}
