package akka.actor.impl.typed;

import akka.actor.ActorSystem;
import akka.actor.typed.scaladsl.ActorContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class StashBufferImplStashAdvice {

  @Advice.OnMethodExit
  public static void enter(
      @Advice.FieldValue("akka$actor$typed$internal$StashBufferImpl$$ctx")
          ActorContext<?> context) {

    akka.actor.ActorContext classicContext = context.classicActorContext();
    Attributes attrs =
        VirtualField.find(akka.actor.ActorContext.class, Attributes.class).get(classicContext);
    Instruments instruments =
        VirtualField.find(ActorSystem.class, Instruments.class).get(classicContext.system());
    ActorCellInstrumentationState state =
        VirtualField.find(akka.actor.ActorContext.class, ActorCellInstrumentationState.class)
            .get(classicContext);

    if (Objects.nonNull(attrs) && Objects.nonNull(state) && Objects.nonNull(instruments)) {
      instruments.stashed().add(1L, attrs);
    }
  }
}
