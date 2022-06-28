package akka.persistence.typed;

import akka.actor.ActorContext;
import akka.actor.ClassicActorContextProvider;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.InstrumentsProvider;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class StoringSnapshotOnSaveSnapshotResponseAdvice {

  @Advice.OnMethodEnter
  public static void enter(
      @Advice.FieldValue("context") ClassicActorContextProvider contextProvider) {

    ActorContext context = contextProvider.classicActorContext();
    PersistenceContext persistenceContext =
        VirtualField.find(ActorContext.class, PersistenceContext.class).get(context);

    if (Objects.nonNull(persistenceContext)) {

      InstrumentsProvider.instance().snapshot().add(1L, persistenceContext.attributes());
    }
  }
}
