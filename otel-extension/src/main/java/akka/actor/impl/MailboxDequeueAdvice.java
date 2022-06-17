package akka.actor.impl;

import akka.actor.ActorContext;
import akka.actor.ActorSystem;
import akka.dispatch.Envelope;
import akka.dispatch.Mailbox;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.util.Interval$;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.util.EnvelopeContext;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class MailboxDequeueAdvice {

  @Advice.OnMethodExit
  public static void exit(@Advice.Return Envelope envelope, @Advice.This Mailbox self) {
    if (Objects.nonNull(self.actor())
        && Objects.nonNull(self.actor().getSystem())
        && Objects.nonNull(envelope)) {
      EnvelopeContext context =
          VirtualField.find(Envelope.class, EnvelopeContext.class).get(envelope);
      Attributes attrs = VirtualField.find(ActorContext.class, Attributes.class).get(self.actor());
      Instruments instruments =
          VirtualField.find(ActorSystem.class, Instruments.class).get(self.actor().getSystem());

      if (Objects.nonNull(instruments) && Objects.nonNull(context) && Objects.nonNull(attrs)) {
        long interval = Interval$.MODULE$.toMillis(System.nanoTime() - context.sentTime());
        instruments.mailboxTime().record(interval, attrs);
      }
    }
  }
}
