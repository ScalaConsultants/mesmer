package akka.actor.impl;

import akka.actor.ActorContext;
import akka.dispatch.Mailbox;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.InstrumentsProvider;
import net.bytebuddy.asm.Advice;

import java.util.Objects;

public class MailboxEnqueueAdvice {

  @Advice.OnMethodExit
  public static void exit(@Advice.This Mailbox self) {
    if (Objects.nonNull(self.actor())
        && Objects.nonNull(self.actor().getSystem())) {
      Attributes attrs = VirtualField.find(ActorContext.class, Attributes.class).get(self.actor());
      Instruments instruments = InstrumentsProvider.instance();

      if (Objects.nonNull(attrs)) {
        instruments.mailboxSize().add(1, attrs);
      }
    }
  }
}
