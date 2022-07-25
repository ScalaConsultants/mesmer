package akka.persistence.typed;

import akka.actor.ActorContext;
import akka.actor.typed.scaladsl.AbstractBehavior;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.InstrumentsProvider;
import io.scalac.mesmer.otelextension.instrumentations.akka.persistence.impl.PersistenceContext;
import io.scalac.subst.Dummy;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class StoringSnapshotOnSaveSnapshotResponseAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.This AbstractBehavior<?> self) {

    ActorContext context = Dummy.dummyContext(self).classicActorContext();
    PersistenceContext persistenceContext =
        VirtualField.find(ActorContext.class, PersistenceContext.class).get(context);

    if (Objects.nonNull(persistenceContext)) {

      InstrumentsProvider.instance().snapshots().add(1L, persistenceContext.attributes());
    }
  }
}
