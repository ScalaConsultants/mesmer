package akka.persistence.typed;

import akka.actor.ActorContext;
import akka.actor.ClassicActorContextProvider;
import akka.persistence.typed.internal.BehaviorSetup;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext;
import net.bytebuddy.asm.Advice;

public class ReplayingSnapshotOnRecoveryStartedAdvice {

  @Advice.OnMethodEnter
  public static void enter(
      @Advice.Argument(0) ClassicActorContextProvider contextProvider,
      @Advice.FieldValue("setup") BehaviorSetup<?, ?, ?> setup) {
    ActorContext context = contextProvider.classicActorContext();
    PersistenceContext persistenceContext =
        PersistenceContext.create(context.self(), setup.persistenceId());
    VirtualField.find(ActorContext.class, PersistenceContext.class)
        .set(context, persistenceContext);
    persistenceContext.startTimer();
  }
}
