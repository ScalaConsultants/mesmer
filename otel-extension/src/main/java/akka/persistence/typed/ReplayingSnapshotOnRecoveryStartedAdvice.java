package akka.persistence.typed;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.actor.ClassicActorContextProvider;
import akka.persistence.typed.internal.BehaviorSetup;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContextProvider;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ReplayingSnapshotOnRecoveryStartedAdvice {

  @Advice.OnMethodEnter
  public static void enter(
      @Advice.Argument(0) ClassicActorContextProvider contextProvider,
      @Advice.FieldValue("setup") BehaviorSetup<?, ?, ?> setup) {

    ActorSystem system = contextProvider.classicActorContext().system();
    PersistenceContextProvider provider =
        VirtualField.find(ActorSystem.class, PersistenceContextProvider.class).get(system);
    if (Objects.nonNull(provider)) {
      ActorContext context = contextProvider.classicActorContext();
      PersistenceContext persistenceContext =
          provider.create(context.self(), setup.persistenceId());
      VirtualField.find(ActorContext.class, PersistenceContext.class)
          .set(context, persistenceContext);
      persistenceContext.startTimer();
    }
  }
}
