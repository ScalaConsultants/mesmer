package akka.actor.impl;

import akka.actor.ActorContext;
import akka.dispatch.Envelope;
import akka.dispatch.Mailbox;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.scalac.mesmer.core.util.Interval;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider;
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
      Instruments instruments = InstrumentsProvider.instance();

      if (Objects.nonNull(context) && Objects.nonNull(attrs)) {
        long interval = new Interval(System.nanoTime() - context.sentTime()).toMillis();

        instruments.mailboxTime().record(interval, attrs);
        instruments.mailboxSize().add(-1, attrs);
      }
    }
  }
}
