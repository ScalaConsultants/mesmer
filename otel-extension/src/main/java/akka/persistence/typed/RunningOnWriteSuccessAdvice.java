package akka.persistence.typed;

import akka.actor.ActorContext;
import akka.actor.ClassicActorContextProvider;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.InstrumentsProvider;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class RunningOnWriteSuccessAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.Argument(0) ClassicActorContextProvider contextProvider) {

    ActorContext context = contextProvider.classicActorContext();
    PersistenceContext persistenceContext =
        VirtualField.find(ActorContext.class, PersistenceContext.class).get(context);

    if (Objects.nonNull(persistenceContext)) {
      long millis = persistenceContext.stopTimer();

      InstrumentsProvider.instance()
          .persistentEventTime()
          .record(millis, persistenceContext.attributes());
    }
  }
}
