package akka.actor.impl;

import akka.actor.*;
import akka.dispatch.Envelope;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.impl.otel.ActorCellInstrumentationState;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class DispatchSendMessageAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.Argument(0) Envelope envelope) {
    if (Objects.nonNull(envelope)) {
      VirtualField.find(Envelope.class, EnvelopeContext.class)
          .set(envelope, EnvelopeContext.apply(System.nanoTime()));

      // we filter out typed actors
      ActorRef sender = envelope.sender();
      if (sender != Actor.noSender() && sender instanceof ActorRefWithCell) {
        ActorContext context = (ActorCell) ((ActorRefWithCell) sender).underlying();
        Attributes attrs = VirtualField.find(ActorContext.class, Attributes.class).get(context);
        Instruments instruments =
            VirtualField.find(ActorSystem.class, Instruments.class).get(context.system());
        ActorCellInstrumentationState state =
            VirtualField.find(ActorContext.class, ActorCellInstrumentationState.class).get(context);

        if (Objects.nonNull(attrs) && Objects.nonNull(state) && Objects.nonNull(instruments)) {
          instruments.sent().add(1L, attrs);
        }
      }
    }
  }
}
