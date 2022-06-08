package akka.actor.impl;

import akka.actor.ActorContext;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.Instruments;
import java.util.Objects;
import net.bytebuddy.asm.Advice;

public class ActorCellReceiveMessageAdvice {

  @Advice.OnMethodEnter
  public static void enter(@Advice.This ActorContext self) {

    Attributes attrs = VirtualField.find(ActorContext.class, Attributes.class).get(self);

    if (Objects.nonNull(attrs)) {
      Instruments.receivedMessages().add(1L, attrs);
    }
  }
}
