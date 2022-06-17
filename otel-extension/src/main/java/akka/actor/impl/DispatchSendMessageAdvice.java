package akka.actor.impl;

import akka.dispatch.Envelope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class DispatchSendMessageAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.Argument(0) Envelope envelope) {
    if (Objects.nonNull(envelope)) {
      VirtualField.find(Envelope.class, EnvelopeContext.class)
          .set(envelope, EnvelopeContext.apply(System.nanoTime()));
    }
  }
}
