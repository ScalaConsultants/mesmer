package akka.actor.impl;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.scalac.mesmer.core.actor.ActorRefAttributeFactory;
import io.scalac.mesmer.otelextension.instrumentations.akka.actor.extension.Mailbox;
import net.bytebuddy.asm.Advice;

public class MailboxSizeAdvice {

  @Advice.OnMethodExit
  public static void actorOfExit(
      @Advice.Argument(0) ActorSystem system, @Advice.Return ActorRef ref) {

    // TODO: Maybe there's no need to duplicate getting the attributes. Do I have the actor context
    // to get them form VF?
    ActorRefAttributeFactory config =
        VirtualField.find(ActorSystem.class, ActorRefAttributeFactory.class)
            .get(system.classicSystem());

    if (config != null) {
      Attributes attributes =
          config.forActorPath(ref.path()).toBuilder()
              .put("full_path", ref.path().toString())
              .build();

      // TODO: Autocloseable???
      Mailbox.initMailboxSizeGauge(ref, attributes);
    }
  }
}
